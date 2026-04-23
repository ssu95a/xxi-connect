package ru.inversion.msrv.validation;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import ru.inversion.msrv.config.Config;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.io.RawBAOS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static ru.inversion.msrv.config.Config.Namespace.BOOT;
import static ru.inversion.msrv.validation.Errors.ErrorCode.*;
import static ru.inversion.utils.LimitedInputStream.EMPTY_STREAM;

public class RequestBodyValidator {

    private final int maxBytes;

    public RequestBodyValidator(Config config) {
        maxBytes = config.get( BOOT.resolve("http.maxBodyBytes"), Integer.class, 65000 );
    }

    public IDco validate( HttpServletRequest request) {

        final String method = request.getMethod();

        if( !"POST".equalsIgnoreCase(method) )
            throw Errors.badRequest( "POST method required");

        String ct = request.getContentType();
        ct = (ct == null ? null : ct.toLowerCase(Locale.ROOT));
        if (ct == null || (!ct.startsWith("application/xml") && !ct.startsWith("text/xml")))
            throw Errors.of( REQUEST_CONTENT_TYPE_INVALID, "application/xml required");

        long cl = request.getContentLengthLong();

        if( cl > maxBytes )
            throw Errors.tooLarge( "Request too large");

        final InputStream is;
        try {

            is = readBodyLimited(request, maxBytes);

            if( is == null)
                throw Errors.tooLarge("Request too large");

            if (is == EMPTY_STREAM)
                throw Errors.badRequest("Empty request body");

        } catch( IOException ex ) {
            throw Errors.badRequest( ex, "Error on load request body");
        }

        final Charset cs = detectCharset(request);

        try {
            return Dco.parseXml(is, cs);
        } catch( Exception ex ) {
            throw Errors.of( REQUEST_XML_MALFORMED, "Malformed XML", ex );
        }
    }

    private static Charset detectCharset(HttpServletRequest req) {
        String enc = req.getCharacterEncoding();
        if (enc == null || enc.isBlank())
            return StandardCharsets.UTF_8;

        try {
            return Charset.forName(enc);
        } catch (Exception ignore) {
            return StandardCharsets.UTF_8;
        }
    }

    private static InputStream readBodyLimited(HttpServletRequest req, int maxBytes) throws IOException {
        try (ServletInputStream in = req.getInputStream()) {
            final RawBAOS out = new RawBAOS(Math.min(4096, Math.max(0, req.getContentLength())));
            final byte[] buf = new byte[4096];
            int total = 0;

            for (;;) {
                int r = in.read(buf);
                if (r < 0)
                    break;
                if (r == 0)
                    continue;

                total += r;
                if (total > maxBytes)
                    return null;

                out.write(buf, 0, r);
            }

            if (out.count() == 0)
                return EMPTY_STREAM;

            return out.inputStream();
        }
    }
}