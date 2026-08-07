package io.github.t3wv.nfse.transformers;

import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.transform.Transform;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NFSeBooleanTransformer implements Transform<Boolean> {
    private static final List<String> TRUE_VALUES = Collections.unmodifiableList(Arrays.asList("1", "true", "yes", "s","sim", "verdadeiro"));
    private static final List<String> FALSE_VALUES = Collections.unmodifiableList(Arrays.asList("0", "false", "no", "n","nao", "não", "falso"));

    @Override
    public Boolean read(String value) {
        if (StringUtils.isNotBlank(value)) {
            String trimmedValue = value.trim().toLowerCase();
            if (TRUE_VALUES.contains(trimmedValue)) {
                return true;
            } else if (FALSE_VALUES.contains(trimmedValue)) {
                return false;
            }
        }
        return null;
    }

    @Override
    public String write(Boolean value) {
        return value ? "1" : "0";
    }
}
