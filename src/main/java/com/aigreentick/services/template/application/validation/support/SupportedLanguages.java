package com.aigreentick.services.template.application.validation.support;

import java.util.Set;

/**
 * Meta's supported template locales. A free-text language field is one of the
 * most common rejection causes, so this is a whitelist rather than a format check.
 * Extend as Meta adds locales.
 */
public final class SupportedLanguages {

    private static final Set<String> CODES = Set.of(
            "af","sq","ar","az","bn","bg","ca","zh_CN","zh_HK","zh_TW","hr","cs","da","nl",
            "en","en_GB","en_US","et","fil","fi","fr","ka","de","el","gu","ha","he","hi","hu",
            "id","ga","it","ja","kn","kk","rw_RW","ko","ky_KG","lo","lv","lt","mk","ms","ml",
            "mr","nb","fa","pl","pt_BR","pt_PT","pa","ro","ru","sr","si_LK","sk","sl","es",
            "es_AR","es_ES","es_MX","sw","sv","ta","te","th","tr","uk","ur","uz","vi","zu"
    );

    private SupportedLanguages() {}

    public static boolean isSupported(String code) {
        return code != null && CODES.contains(code.trim());
    }
}