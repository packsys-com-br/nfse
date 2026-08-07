#!/bin/bash
#
# Script para verificar a sincronização do fork 'nfse-t3wv-fork' com o
# repositório original 't3wv/nfse' e automatizar a atualização de versão:
#
# 1. Detecta a última versão publicada pelo T3W (tags do upstream).
# 2. Se houver versão nova, pergunta se deseja atualizar. Ao confirmar:
#    - Cria a branch nfse_XXXX (ex: 1.0.45 -> nfse_1045) a partir da branch atual.
#    - Faz o merge da tag da nova versão.
#    - Resolve automaticamente os conflitos conhecidos:
#        * arquivos de teste removidos no fork -> mantém removidos;
#        * pom.xml -> aceita o do upstream e reaplica o patch local de
#          java.version (configurável abaixo).
#    - Compila o projeto com --release 8 para detectar código novo que use
#      sintaxe do Java 9+ (var, java.net.http, .formatted(), switch
#      expressions, etc.) — isso NÃO é reaplicável via sed como o
#      java.version; precisa de correção manual (ou com ajuda do Claude
#      Code) antes de seguir.
#    - Oferece instalar a lib localmente (mvn install) e o push da branch
#      (substitui o botão 'Sync Fork' do GitHub).
#    - Oferece preparar o projeto consumidor (PROJETO_DIR): cria a mesma
#      branch nfse_XXXX e atualiza a propriedade <nfse.version> no pom.xml.
# 3. Verifica java.version/release no pom.xml e o status de sincronização
#    das branches.
#
# Passos que continuam manuais: revisar/corrigir incompatibilidades Java 8
# apontadas pela compilação, compilar o projeto consumidor na IDE e testar
# a emissão/consulta de NFS-e.
#

set -e

# --- Configurações ---
UPSTREAM_REPO_URL="https://github.com/t3wv/nfse.git"
UPSTREAM_REMOTE="upstream"
UPSTREAM_BRANCH="main"

# Patch local reaplicado no pom.xml após o merge de uma nova versão.
# (t3wv/nfse não depende de xmlsec, então só há este patch de java.version;
#  a diretiva <release>8</release> do compiler-plugin também é reaplicada
#  logo abaixo, na resolve_merge_conflicts.)
LOCAL_JAVA_VERSION="1.8"

# Projeto consumidor da biblioteca (recebe branch nova e atualização do <nfse.version>)
PROJETO_DIR="/Users/ericgravescamollez/Dev/Projects/NetBeansProjects/ipack-git"
PROJETO_NFSE_PROPERTY="nfse.version"

# --- Cores para o output ---
COLOR_GREEN='\033[0;32m'
COLOR_YELLOW='\033[1;33m'
COLOR_RED='\033[0;31m'
COLOR_BLUE='\033[0;34m'
COLOR_RESET='\033[0m'

# --- Funções auxiliares ---
check_latest_nfse_version() {
    echo -e "${COLOR_BLUE}Verificando a última versão da biblioteca NFS-e (t3wv/nfse)...${COLOR_RESET}"

    # Versão local declarada no pom.xml (primeira ocorrência de <version> = versão do projeto)
    LOCAL_VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')

    # Última tag publicada no repositório original (ex: v1.0.43)
    LATEST_TAG=$(git ls-remote --tags --sort=-v:refname ${UPSTREAM_REPO_URL} 'v*' 2>/dev/null \
        | grep -v '\^{}' | head -n1 | sed -E 's/.*refs\/tags\/v?//')

    if [[ -z "$LATEST_TAG" ]]; then
        echo -e "  - ${COLOR_RED}Não foi possível consultar as tags do repositório original (sem conexão?).${COLOR_RESET}"
        echo ""
        return
    fi

    # Remove sufixo -SNAPSHOT para comparação
    LOCAL_VERSION_CLEAN="${LOCAL_VERSION%-SNAPSHOT}"

    echo -e "  - Versão local (pom.xml): ${LOCAL_VERSION}"
    echo -e "  - Última versão publicada (t3wv/nfse): ${LATEST_TAG}"

    if [[ "$LOCAL_VERSION_CLEAN" == "$LATEST_TAG" ]]; then
        echo -e "  - ${COLOR_GREEN}🎉 Você está na última versão da NFS-e.${COLOR_RESET}"
    else
        NEWEST=$(printf '%s\n%s\n' "$LOCAL_VERSION_CLEAN" "$LATEST_TAG" | sort -V | tail -n1)
        if [[ "$NEWEST" == "$LATEST_TAG" ]]; then
            echo -e "  - ${COLOR_YELLOW}⚠️ Existe uma versão mais recente disponível: ${LATEST_TAG} (local: ${LOCAL_VERSION}).${COLOR_RESET}"
            ask_to_update
        else
            echo -e "  - ${COLOR_GREEN}Sua versão local (${LOCAL_VERSION}) é mais recente que a última tag publicada (${LATEST_TAG}).${COLOR_RESET}"
        fi
    fi
    echo ""
}

resolve_merge_conflicts() {
    echo -e "  - ${COLOR_YELLOW}O merge encontrou conflitos. Tentando resolver automaticamente os conflitos conhecidos...${COLOR_RESET}"

    # 1. Arquivos removidos no fork mas modificados no upstream: mantém removidos
    git status --porcelain | awk '$1 == "DU"' | cut -c4- | while IFS= read -r FILE; do
        echo "    - Mantendo removido: ${FILE}"
        git rm --quiet -- "$FILE"
    done

    # 2. pom.xml: usa a versão do upstream e reaplica os patches locais
    if git status --porcelain | grep -q '^UU pom.xml'; then
        echo "    - pom.xml: usando a versão nova do upstream e reaplicando patch local (java ${LOCAL_JAVA_VERSION})..."
        git checkout --theirs -- pom.xml
        sed -i '' -E "s|<java.version>[^<]*</java.version>|<java.version>${LOCAL_JAVA_VERSION}</java.version>|" pom.xml
        # Compiler-plugin: troca <source>${java.version}</source><target>${java.version}</target>
        # (padrão do upstream, Java 21) por <release>8</release> (nosso patch).
        perl -0777 -pi -e 's{<source>\$\{java\.version\}</source>\s*\n\s*<target>\$\{java\.version\}</target>}{<release>8</release>}' pom.xml
        git add pom.xml

        if ! grep -q '<release>8</release>' pom.xml; then
            echo -e "    - ${COLOR_RED}⚠️ Não encontrei o padrão esperado do compiler-plugin para reaplicar <release>8</release> automaticamente.${COLOR_RESET}"
            echo "      Verifique manualmente o maven-compiler-plugin no pom.xml antes de continuar."
        fi
    fi

    # Se ainda restar algum conflito desconhecido, não arrisca: deixa para resolução manual
    if [[ -n "$(git diff --name-only --diff-filter=U)" ]]; then
        return 1
    fi
    git commit --no-edit --quiet
    return 0
}

check_java8_compile() {
    echo ""
    echo -e "${COLOR_BLUE}Compilando com --release 8 para detectar incompatibilidades Java 9+ trazidas pelo merge...${COLOR_RESET}"
    echo "  (isso NÃO é reaplicável via sed como o java.version — código novo do upstream que use"
    echo "   var/switch expressions/java.net.http/etc. precisa ser corrigido manualmente antes de seguir)"

    COMPILE_LOG=$(mktemp)
    if mvn -q -Dmaven.test.skip=true compile > "$COMPILE_LOG" 2>&1; then
        echo -e "  - ${COLOR_GREEN}🎉 Compilação OK — nenhuma incompatibilidade com Java 8 encontrada.${COLOR_RESET}"
        rm -f "$COMPILE_LOG"
        return 0
    else
        echo -e "  - ${COLOR_RED}⚠️ A compilação falhou. Provavelmente o merge trouxe código com sintaxe do Java 9+.${COLOR_RESET}"
        echo "  --- Erros de compilação ---"
        grep '^\[ERROR\]' "$COMPILE_LOG" | sed 's/^/    /'
        echo "  ----------------------------"
        echo "    Corrija os erros acima (ou peça ajuda ao Claude Code, que já fez esse tipo de"
        echo "    adaptação neste projeto — ver commit 'Downgrade compatibilidade para Java 8'),"
        echo "    rode 'mvn -Dmaven.test.skip=true compile' de novo para confirmar, e finalize o"
        echo "    commit do merge (o commit ainda NÃO foi feito) antes de instalar/publicar."
        rm -f "$COMPILE_LOG"
        return 1
    fi
}

offer_install() {
    echo ""
    read -r -p "  Deseja instalar a biblioteca localmente agora (mvn -Dmaven.test.skip=true install)? (s/N) " RESPOSTA_INSTALL
    if [[ "$RESPOSTA_INSTALL" =~ ^[SsYy]$ ]]; then
        mvn -q -Dmaven.test.skip=true install
        echo -e "  - ${COLOR_GREEN}Instalado em ~/.m2 como io.github.t3wv:nfse:${LATEST_TAG}.${COLOR_RESET}"
    else
        echo -e "  - ${COLOR_YELLOW}Instalação adiada. Rode depois com: mvn -Dmaven.test.skip=true install${COLOR_RESET}"
    fi
}

update_consumer_project() {
    if [[ ! -d "${PROJETO_DIR}/.git" ]]; then
        return
    fi
    PROJETO_NOME=$(basename "${PROJETO_DIR}")

    echo ""
    read -r -p "  Deseja preparar também o projeto '${PROJETO_NOME}' (criar branch '${NEW_BRANCH}' e atualizar <${PROJETO_NFSE_PROPERTY}> para ${LATEST_TAG})? (s/N) " RESPOSTA_PROJETO
    if [[ ! "$RESPOSTA_PROJETO" =~ ^[SsYy]$ ]]; then
        echo -e "  - ${COLOR_YELLOW}Projeto '${PROJETO_NOME}' não alterado. Lembre-se de criar a branch e atualizar o pom.xml manualmente.${COLOR_RESET}"
        return
    fi

    if git -C "${PROJETO_DIR}" show-ref --verify --quiet "refs/heads/${NEW_BRANCH}"; then
        echo -e "  - ${COLOR_YELLOW}A branch '${NEW_BRANCH}' já existe em '${PROJETO_NOME}'. Mudando para ela...${COLOR_RESET}"
        git -C "${PROJETO_DIR}" checkout --quiet "${NEW_BRANCH}"
    else
        echo "  - Criando a branch '${NEW_BRANCH}' em '${PROJETO_NOME}' (a partir de $(git -C "${PROJETO_DIR}" rev-parse --abbrev-ref HEAD))..."
        git -C "${PROJETO_DIR}" checkout --quiet -b "${NEW_BRANCH}"
    fi

    sed -i '' -E "s|<${PROJETO_NFSE_PROPERTY}>[^<]*</${PROJETO_NFSE_PROPERTY}>|<${PROJETO_NFSE_PROPERTY}>${LATEST_TAG}</${PROJETO_NFSE_PROPERTY}>|" "${PROJETO_DIR}/pom.xml"
    NOVA_PROP=$(grep -o "<${PROJETO_NFSE_PROPERTY}>[^<]*</${PROJETO_NFSE_PROPERTY}>" "${PROJETO_DIR}/pom.xml")
    echo -e "  - ${COLOR_GREEN}pom.xml de '${PROJETO_NOME}' atualizado: ${NOVA_PROP}${COLOR_RESET}"
    echo "    (Alteração NÃO commitada — revise na IDE antes de commitar.)"
}

show_final_reminder() {
    echo ""
    echo -e "${COLOR_BLUE}--- Próximos passos (manuais) ---${COLOR_RESET}"
    echo "  1. Compile o projeto NFS-e (fork) na IDE, se ainda não tiver feito pela linha de comando."
    echo "  2. Compile o seu projeto ($(basename "${PROJETO_DIR}"))."
    echo "  3. Teste a emissão/consulta das NFS-e antes de fazer commit/push definitivo."
}

show_available_changes() {
    # Lista os commits do upstream entre a versão local e a nova (requer as tags,
    # baixadas no fetch inicial). Oculta os commits automáticos do maven-release-plugin.
    if ! git rev-parse -q --verify "v${LOCAL_VERSION_CLEAN}" > /dev/null 2>&1 || \
       ! git rev-parse -q --verify "v${LATEST_TAG}" > /dev/null 2>&1; then
        return
    fi
    echo ""
    echo -e "${COLOR_BLUE}  --- Alterações disponíveis (v${LOCAL_VERSION_CLEAN} -> v${LATEST_TAG}) ---${COLOR_RESET}"
    git log --oneline --no-decorate --no-merges "v${LOCAL_VERSION_CLEAN}..v${LATEST_TAG}" \
        | grep -vE '\[maven-release-plugin\]' \
        | sed 's/^/    /'
    TOTAL_COMMITS=$(git rev-list --count "v${LOCAL_VERSION_CLEAN}..v${LATEST_TAG}")
    echo -e "    ${COLOR_YELLOW}(${TOTAL_COMMITS} commits no total; commits automáticos de release ocultados)${COLOR_RESET}"
}

ask_to_update() {
    show_available_changes

    # Se não estiver rodando em um terminal interativo, apenas informa e não pergunta
    if [[ ! -t 0 ]]; then
        echo "    Recomendação: Execute o script em um terminal para poder atualizar interativamente."
        return
    fi

    # Nome da nova branch derivado da versão: 1.0.45 -> nfse_1045
    NEW_BRANCH="nfse_$(echo "$LATEST_TAG" | tr -d '.')"

    echo ""
    read -r -p "  Deseja criar a branch '${NEW_BRANCH}' e atualizá-la para a versão ${LATEST_TAG} agora? (s/N) " RESPOSTA
    if [[ "$RESPOSTA" =~ ^[SsYy]$ ]]; then
        echo ""
        echo -e "${COLOR_BLUE}Atualizando para a versão ${LATEST_TAG}...${COLOR_RESET}"
        git fetch --quiet ${UPSTREAM_REMOTE} --tags

        if git show-ref --verify --quiet "refs/heads/${NEW_BRANCH}"; then
            echo -e "  - ${COLOR_YELLOW}A branch '${NEW_BRANCH}' já existe. Mudando para ela...${COLOR_RESET}"
            git checkout "${NEW_BRANCH}"
        else
            echo -e "  - Criando a branch '${NEW_BRANCH}' a partir da branch atual ($(git rev-parse --abbrev-ref HEAD))..."
            git checkout -b "${NEW_BRANCH}"
        fi

        MERGE_OK=0
        if git merge --no-edit --quiet "v${LATEST_TAG}" > /dev/null 2>&1; then
            MERGE_OK=1
        elif resolve_merge_conflicts; then
            MERGE_OK=1
        fi

        if [[ "$MERGE_OK" -eq 1 ]]; then
            echo -e "  - ${COLOR_GREEN}🎉 Branch '${NEW_BRANCH}' atualizada com sucesso para a versão ${LATEST_TAG}.${COLOR_RESET}"

            if check_java8_compile; then
                offer_install

                # Push da nova branch (substitui o 'Sync Fork' da página do GitHub)
                echo ""
                read -r -p "  Deseja enviar a branch '${NEW_BRANCH}' para o seu fork no GitHub agora (git push)? (s/N) " RESPOSTA_PUSH
                if [[ "$RESPOSTA_PUSH" =~ ^[SsYy]$ ]]; then
                    git push -u origin "${NEW_BRANCH}"
                    echo -e "  - ${COLOR_GREEN}Branch '${NEW_BRANCH}' enviada para o GitHub.${COLOR_RESET}"
                else
                    echo -e "  - ${COLOR_YELLOW}Push adiado. Envie depois com: git push -u origin ${NEW_BRANCH}${COLOR_RESET}"
                fi

                # Prepara o projeto consumidor (branch + <nfse.version> no pom.xml)
                update_consumer_project
                show_final_reminder
            else
                echo ""
                echo -e "  - ${COLOR_YELLOW}Push, instalação e atualização do projeto consumidor foram pulados até a compilação passar.${COLOR_RESET}"
                echo "    O merge já está commitado nesta branch — corrija o código, comite a correção e"
                echo "    rode este script novamente (ele vai detectar que já está na última versão)."
            fi
        else
            echo -e "  - ${COLOR_RED}⚠️ O merge encontrou conflitos que o script não sabe resolver sozinho.${COLOR_RESET}"
            echo "    Arquivos ainda em conflito:"
            git diff --name-only --diff-filter=U | sed 's/^/      - /'
            echo "    Resolva os conflitos manualmente e finalize com 'git commit',"
            echo "    ou desfaça a tentativa com 'git merge --abort'."
        fi
    else
        echo -e "  - ${COLOR_YELLOW}Atualização adiada. Você pode atualizar depois criando a branch:${COLOR_RESET}"
        echo "    git checkout -b ${NEW_BRANCH} && git merge v${LATEST_TAG}"
    fi
}

check_java_config() {
    echo -e "${COLOR_BLUE}Verificando configuração Java no pom.xml...${COLOR_RESET}"
    POM_FILE="pom.xml"
    JAVA_VERSION=$(grep -E '<java.version>.*</java.version>' "$POM_FILE" | sed -E 's/.*<java.version>(.*)<\/java.version>.*/\1/')

    if [[ "$JAVA_VERSION" == "1.8" ]] && grep -q '<release>8</release>' "$POM_FILE"; then
        echo -e "  - ${COLOR_GREEN}pom.xml: java.version=${JAVA_VERSION}, compiler-plugin com <release>8</release> (OK - Compatível com Java 8).${COLOR_RESET}"
    elif [[ "$JAVA_VERSION" == "1.8" ]]; then
        echo -e "  - ${COLOR_YELLOW}pom.xml: java.version=${JAVA_VERSION}, mas não encontrei <release>8</release> no compiler-plugin (verifique manualmente).${COLOR_RESET}"
    else
        echo -e "  - ${COLOR_RED}pom.xml: java.version=${JAVA_VERSION} (ATENÇÃO: pode não ser compatível com Java 8).${COLOR_RESET}"
    fi
    echo ""
}

# --- Início do Script ---
echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
echo -e "${COLOR_BLUE} Verificador de Sincronização do Fork T3W NFS-e ${COLOR_RESET}"
echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
echo ""

# --- 1. Verifica e configura o remote 'upstream' ---
if ! git remote | grep -q "^${UPSTREAM_REMOTE}$"; then
    echo -e "${COLOR_YELLOW}Configurando o repositório original (upstream) pela primeira vez...${COLOR_RESET}"
    git remote add ${UPSTREAM_REMOTE} ${UPSTREAM_REPO_URL}
    echo -e "${COLOR_GREEN}Repositório '${UPSTREAM_REMOTE}' adicionado com sucesso.${COLOR_RESET}"
    echo ""
fi

# --- 2. Busca as atualizações de todos os remotes ---
echo "Buscando as atualizações mais recentes do seu fork (origin) e do original (upstream)..."
git fetch origin
git fetch --tags ${UPSTREAM_REMOTE}
echo "Busca concluída."
echo ""

# --- 3. Verificação da última versão da NFS-e ---
check_latest_nfse_version

# --- 4. Verificação do pom.xml ---
check_java_config

# --- 5. Comparações de Sincronização ---
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

echo -e "${COLOR_BLUE}--- Status do seu Fork no GitHub (origin/${CURRENT_BRANCH}) vs. T3W Original (upstream/${UPSTREAM_BRANCH}) ---${COLOR_RESET}"
# Se a branch atual ainda não existe no GitHub (recém-criada), avisa e encerra
if ! git show-ref --verify --quiet "refs/remotes/origin/${CURRENT_BRANCH}"; then
    echo -e "${COLOR_YELLOW}⚠️ A branch '${CURRENT_BRANCH}' ainda não existe no seu fork do GitHub.${COLOR_RESET}"
    echo "   Recomendação: Envie-a com 'git push -u origin ${CURRENT_BRANCH}' e rode este script novamente."
    echo ""
    echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
    echo "Verificação de Sincronização Concluída."
    echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
    exit 0
fi

# Compara sua branch de trabalho remota (no seu fork do GitHub) com a main do upstream
COMMITS_BEHIND_UPSTREAM=$(git rev-list --right-only --count origin/${CURRENT_BRANCH}...${UPSTREAM_REMOTE}/${UPSTREAM_BRANCH})
COMMITS_AHEAD_UPSTREAM=$(git rev-list --left-only --count origin/${CURRENT_BRANCH}...${UPSTREAM_REMOTE}/${UPSTREAM_BRANCH})

if [ "$COMMITS_BEHIND_UPSTREAM" -gt 0 ]; then
    echo -e "${COLOR_YELLOW}⚠️ Seu fork no GitHub (origin/${CURRENT_BRANCH}) está ${COMMITS_BEHIND_UPSTREAM} commit(s) ATRÁS do repositório original do T3W (upstream/${UPSTREAM_BRANCH}).${COLOR_RESET}"
    echo "   Recomendação: Vá para a página do seu fork no GitHub e use o botão 'Sync fork' para atualizar."
elif [ "$COMMITS_AHEAD_UPSTREAM" -gt 0 ]; then
    echo -e "${COLOR_GREEN}👍 Seu fork no GitHub (origin/${CURRENT_BRANCH}) tem ${COMMITS_AHEAD_UPSTREAM} commit(s) que não estão no original (OK).${COLOR_RESET}"
else
    echo -e "${COLOR_GREEN}🎉 Seu fork no GitHub (origin/${CURRENT_BRANCH}) está sincronizado com o repositório original.${COLOR_RESET}"
fi
echo ""

echo -e "${COLOR_BLUE}--- Status da sua Branch Local (${CURRENT_BRANCH}) vs. seu Fork no GitHub (origin/${CURRENT_BRANCH}) ---${COLOR_RESET}"
# Compara sua branch de trabalho local com a branch de trabalho remota no seu fork
COMMITS_BEHIND_ORIGIN=$(git rev-list --right-only --count ${CURRENT_BRANCH}...origin/${CURRENT_BRANCH})
COMMITS_AHEAD_ORIGIN=$(git rev-list --left-only --count ${CURRENT_BRANCH}...origin/${CURRENT_BRANCH})

if [ "$COMMITS_BEHIND_ORIGIN" -gt 0 ]; then
    echo -e "${COLOR_YELLOW}⚠️ Sua branch local '${CURRENT_BRANCH}' está ${COMMITS_BEHIND_ORIGIN} commit(s) ATRÁS da sua branch remota (origin/${CURRENT_BRANCH}).${COLOR_RESET}"
    echo "   Recomendação: Execute 'git pull origin ${CURRENT_BRANCH}' (ou use o GitHub Desktop) para buscar as últimas alterações."
elif [ "$COMMITS_AHEAD_ORIGIN" -gt 0 ]; then
    echo -e "${COLOR_YELLOW}⬆️ Sua branch local '${CURRENT_BRANCH}' tem ${COMMITS_AHEAD_ORIGIN} commit(s) que ainda não foram enviados para o seu fork no GitHub.${COLOR_RESET}"
    echo "   Recomendação: Execute 'git push origin ${CURRENT_BRANCH}' (ou use o GitHub Desktop) para enviar suas alterações."
else
    echo -e "${COLOR_GREEN}🎉 Sua branch local '${CURRENT_BRANCH}' está sincronizada com seu fork no GitHub.${COLOR_RESET}"
fi
echo ""

echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
echo "Verificação de Sincronização Concluída."
echo -e "${COLOR_BLUE}===================================================================${COLOR_RESET}"
