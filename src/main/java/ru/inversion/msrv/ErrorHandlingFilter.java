package ru.inversion.msrv;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.MDC;
import ru.inversion.msrv.metrics.Metrics;
import ru.inversion.msrv.validation.Errors;
import ru.inversion.msrv.validation.UnknownAliasException;
import ru.inversion.msrv.validation.XXIConnectException;
import ru.inversion.utils.S;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

public final class ErrorHandlingFilter implements Filter {

    private static final Logger logger = getLogger( MethodHandles.lookup().lookupClass() );

    public static final String REQ_ID_ATTR   = "reqId";
    public static final String REQ_ID_HEADER = "X-Request-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        final HttpServletRequest  req = (HttpServletRequest ) request;
        final HttpServletResponse rsp = (HttpServletResponse) response;

        try( Metrics.Scope span = Metrics.Tools.openSpan( req, "filter", "ErrorHandlingFilter", "wrapper_time") )
        {
            String reqId = req.getHeader(REQ_ID_HEADER);
            if( S.isNullOrEmpty(reqId) )
                reqId = UUID.randomUUID().toString();

            req.setAttribute( REQ_ID_ATTR,   reqId );
            rsp.setHeader   ( REQ_ID_HEADER, reqId );

            MDC.put("rid", reqId );

            span.context().put( Metrics.Key.REQ_ID, reqId );

            try {
                chain.doFilter(request, response);
            }
            catch (Throwable th) {

                span.fail(th);

                if( rsp.isCommitted() )
                {
                    span.context().put(Metrics.Key.EX_CLASS,         th.getClass().getSimpleName());
                    span.context().put(Metrics.Key.RESULT_OUTCOME,  "FAIL");
                    span.context().put(Metrics.Key.RESULT_CLASS,    "COMMITTED_ERROR");
                    span.context().put(Metrics.Key.RESP_HTTP_STATUS, rsp.getStatus());

                    if (th instanceof ServletException se) throw se;
                    if (th instanceof IOException ioe) throw ioe;
                    throw new ServletException(th);
                }

                try {
                    rsp.resetBuffer();
                } catch (IllegalStateException ignored) {
                }

                final XXIConnectException x = (th instanceof XXIConnectException) ? (XXIConnectException) th : null;

                int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                if (x != null) {
                    status = x.getHttpStatus();
                } else if (th instanceof IllegalArgumentException) {
                    status = HttpServletResponse.SC_BAD_REQUEST;
                }

                final Errors.ErrorCode error =
                        (x != null && x.error() != null)
                                ? x.error()
                                : defaultError(status);

                final String code = error.code();
                final String msg = safeMsg(th, x, error, status);
                final Errors.LogPolicy logPolicy = error.logPolicy();

                span.context().put(Metrics.Key.RESP_HTTP_STATUS, status);
                span.context().put(Metrics.Key.RESULT_OUTCOME, "FAIL");
                span.context().put(Metrics.Key.RESULT_ERROR_CODE, code);
                span.context().put(Metrics.Key.RESULT_CLASS, classifyResult(status) );
                span.context().put(Metrics.Key.EX_CLASS, th.getClass().getSimpleName());

                rsp.setStatus(status);
                rsp.setHeader("Cache-Control", "no-store");
                rsp.setHeader("Pragma", "no-cache");

                if( PathTools.isAuth(req) )
                {
                    rsp.setContentType("application/xml; charset=utf-8");
                    rsp.getWriter().write(authErrorXml(reqId, code, msg));
                }
                else
                {
                    rsp.setContentType("text/plain; charset=utf-8");
                    rsp.getWriter().write("ERROR\n");
                    rsp.getWriter().write("status=" + status + "\n");
                    rsp.getWriter().write("requestId=" + reqId + "\n");
                    rsp.getWriter().write("code=" + code + "\n");
                    rsp.getWriter().write("message=" + msg + "\n");
                }

                logFailure(req, status, code, msg, th, logPolicy);
            }
            finally {
                MDC.remove("rid");
            }
        }
    }

    /** */
    private static void logFailure( HttpServletRequest req, int status, String code, String msg, Throwable th, Errors.LogPolicy logPolicy )
    {
        if (th instanceof UnknownAliasException uae) {
            logger.warn(
                    "request.fail uri={} http={} code={} msg={} alias.raw={} alias.nrm={}",
                    req.getRequestURI(),
                    status,
                    code,
                    msg,
                    uae.rawAlias(),
                    uae.normalizedAlias()
            );
            return;
        }

        if (logPolicy == Errors.LogPolicy.ERROR_WITH_STACK) {
            logger.error(
                    "request.fail uri={} http={} code={} msg={}",
                    req.getRequestURI(),
                    status,
                    code,
                    msg,
                    th
            );
            return;
        }

        logger.warn(
                "request.fail uri={} http={} code={} msg={}",
                req.getRequestURI(),
                status,
                code,
                msg
        );
    }

    private static String classifyResult(int status) {
        if (status == 503) return "TARGET_UNAVAILABLE";
        if (status == 401 || status == 403) return "AUTH_FAIL";
        if (status == 409) return "CONFLICT";
        if (status == 400) return "BAD_REQUEST";
        if (status >= 500) return "INTERNAL_ERROR";
        return "FAIL";
    }

    /** */
    private static String authErrorXml( String reqId, String code, String msg )
    {
        final IDco authResponse = new Dco("authResponse");
        authResponse.e("status"   ).set("FAIL");
        authResponse.e("errorCode").set(code);
        authResponse.e("message"  ).set(msg);
        authResponse.e("requestId").set(reqId);
        return authResponse.asXml();
    }

    private static Errors.ErrorCode defaultError(int status) {
        if (status == 400) return Errors.ErrorCode.REQUEST_INVALID;
        if (status == 401) return Errors.ErrorCode.AUTH_CREDENTIALS_INVALID;
        if (status == 403) return Errors.ErrorCode.FORBIDDEN;
        if (status == 409) return Errors.ErrorCode.CONFLICT;
        if (status == 413) return Errors.ErrorCode.REQUEST_PAYLOAD_TOO_LARGE;
        if (status == 503) return Errors.ErrorCode.SERVICE_UNAVAILABLE;
        if (status >= 500) return Errors.ErrorCode.INTERNAL_ERROR;
        return Errors.ErrorCode.INTERNAL_UNEXPECTED;
    }

    /** */
    private static String safeMsg(Throwable th, XXIConnectException x, Errors.ErrorCode error, int status) {

        if (status >= 500)
            return "Internal error";

        String m = th.getMessage();

        if (S.isNullOrEmpty(m) && error != null)
            m = error.externalMessage();

        if (S.isNullOrEmpty(m))
            m = th.getClass().getSimpleName();

        m = m.strip();

        if (m.length() > 200)
            m = m.substring(0, 200) + "...";

        return m;
    }
}