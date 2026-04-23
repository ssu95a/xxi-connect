package ru.inversion.msrv.validation;

import ru.inversion.msrv.InputFilter;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.config.PreparedObjectRegistry;
import ru.inversion.utils.S;
import ru.inversion.utils.dco.IDco;

import java.net.PasswordAuthentication;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static ru.inversion.msrv.validation.Errors.ErrorCode.*;

public class XmlValidator {

    final private PreparedObjectRegistry registry;

    public XmlValidator(PreparedObjectRegistry registry) {
        this.registry = registry;
    }

    public void validateAlias(String alias) {

        if( alias == null )
            throw Errors.of( REQUEST_TARGET_ALIAS_MISSING, "'alias' is missing");

        if( alias.isEmpty() )
            throw Errors.of( REQUEST_TARGET_ALIAS_MISSING, "'alias' is empty");

        if( alias.length() > registry.config().get("alias.maxLength", Integer.class, 256 ) )
            throw Errors.of( REQUEST_TARGET_ALIAS_INVALID, "'alias' too long");

        if( !registry.regex("alias.matchPattern","^[A-Za-z0-9_.-:/\\-]+$", 0 ).matcher(alias).matches() )
            throw Errors.of(REQUEST_TARGET_ALIAS_INVALID, "Alias contains illegal characters");
    }

    /** */
    public void validateDco(IDco dco, BiConsumer<String, Object> attrConsumer) {

        final String v = value(dco, "/authRequest/@v", true, 0, "version", true);

        if( !"1".equals(v) )
            throw Errors.badXmlElemValue( REQUEST_VALUE_INVALID, "version", "Unsupported authRequest@v=" + v);

        final String login = value(dco, "/authRequest/user/login", true, 50, "user.login", true);
        final char[] password = readPassword(dco);

        attrConsumer.accept( InputFilter.AUTH_LOGIN_ATTR, login );
        attrConsumer.accept( InputFilter.AUTH_CREDENTIAL_ATTR, new PasswordAuthentication(login, password) );
    }

    private char[] readPassword(IDco dco) {
        return value(dco, "/authRequest/user/password", true, 128, "user.password", false).toCharArray();
    }

    /** */
    private static String value(IDco dco, String xpath, boolean required, int maxLen, String field, boolean trim )
    {
        final String s = dco.single( xpath, d -> {
            Object v = d.get();
            if( v == null )
                return null;
            String x = v.toString();
            return trim ? x.trim() : x;
        }).orElse(null);

        if( required && S.isNullOrEmpty(s) )
            throw Errors.badXmlElemValue( REQUEST_VALUE_MISSING, field, null );

        if( !S.isNullOrEmpty(s) && maxLen > 0 && s.length() > maxLen )
            throw Errors.badXmlElemValue( REQUEST_VALUE_TOO_LONG, field, "Value too long" );

        return s;
    }
}