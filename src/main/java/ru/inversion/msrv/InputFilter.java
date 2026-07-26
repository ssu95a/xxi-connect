package ru.inversion.msrv;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.config.PreparedObjectRegistry;
import ru.inversion.msrv.metrics.Metrics;
import ru.inversion.msrv.validation.*;
import ru.inversion.utils.U;
import ru.inversion.utils.dco.IDco;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class InputFilter implements Filter {

    public static final String AUTH_CREDENTIAL_ATTR = "auth.cred";
    public static final String AUTH_ALIAS_ATTR      = "auth.alias";
    public static final String AUTH_LOGIN_ATTR      = "auth.login";
    public static final String TARGET_CTX_ATTR      = "target.ctx";

    private final TargetRegistry targetRegistry;
    private final RequestBodyValidator rbv;
    private final XmlValidator xv;

    public InputFilter(TargetRegistry targetRegistry, PreparedObjectRegistry registry ) {

        this.targetRegistry = Objects.requireNonNull( targetRegistry, "'targetRegistry' is null ");

        this.rbv = new RequestBodyValidator(registry.config());
        this.xv  = new XmlValidator(registry);
    }

    @Override
    public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest) request;

        if (!PathTools.isAuth(req)) {
            chain.doFilter(request, response);
            return;
        }

        final Metrics.Scope span = Metrics.Tools.openSpan(req, "filter", "InputFilter", "self_time");
        final Metrics.Context mc = span.context();

        try {

            final IDco dco = rbv.validate(req);

            final String alias = dco.single("/authRequest/target/alias")
                    .map(IDco::value)
                        .map(v -> v.toString().trim())
                    .orElse(null);

            xv.validateAlias(alias);
            xv.validateDco( dco, req::setAttribute );

            final TargetContext targetContext = targetRegistry.resolve(alias);

            req.setAttribute( TARGET_CTX_ATTR, targetContext );
            req.setAttribute( AUTH_ALIAS_ATTR, targetContext.alias() );

            mc.put( Metrics.Key.AUTH_ALIAS, targetContext.alias());
            mc.put( Metrics.Key.AUTH_TARGET_VENDOR, targetContext.vendorDb().name());
        }
        catch (Exception ex) {
            span.fail(ex);
            throw ex;
        }
        finally {
            span.close();
        }

        chain.doFilter(request, response);
    }
}