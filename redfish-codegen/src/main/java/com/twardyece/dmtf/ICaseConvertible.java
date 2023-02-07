package com.twardyece.dmtf;

import java.util.Collection;

public interface ICaseConvertible {
    public Collection<? extends IWord> words();

    public class CaseConversionError extends RuntimeException {
        private String targetCase;
        private String text;

        public CaseConversionError(String targetCase, String text) {
            super("String " + text + " is not convertible to " + targetCase);
        }
    }
}
