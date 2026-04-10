import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class WebDAVServer {
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";
    private static int PORT = 80;
    private static Path rootDir;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            try { PORT = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        rootDir = Paths.get("webdav_share").toAbsolutePath();
        Files.createDirectories(rootDir);
        createDemoData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new WebDAVHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        String ip = java.net.InetAddress.getLocalHost().getHostAddress();
        System.out.println("========================================");
        System.out.println("  WebDAV Server Running");
        System.out.println("========================================");
        System.out.println("  Local:   http://localhost:" + PORT);
        System.out.println("  Network: http://" + ip + ":" + PORT);
        System.out.println("  User:    " + USERNAME);
        System.out.println("  Pass:    " + PASSWORD);
        System.out.println("  Root:    " + rootDir);
        System.out.println("========================================");
        System.out.println("  Press Ctrl+C to stop");
        System.out.println("========================================");
    }

    static void createDemoData() throws Exception {
        Path docs = rootDir.resolve("\u6587\u6863");
        Path photos = rootDir.resolve("\u7167\u7247");
        Path music = rootDir.resolve("\u97f3\u4e50");
        Path projects = rootDir.resolve("\u9879\u76ee\u8d44\u6599");
        Files.createDirectories(docs);
        Files.createDirectories(photos);
        Files.createDirectories(music);
        Files.createDirectories(projects);

        writeFile(docs.resolve("readme.txt"),
            "\u6b22\u8fce\u4f7f\u7528\u8fdc\u7a0b\u6587\u4ef6\u7ba1\u7406\u5668\uff01\n" +
            "\u8fd9\u662f\u4e00\u4e2a\u6f14\u793a\u6587\u4ef6\u3002\n" +
            "\u652f\u6301 WebDAV \u548c SMB3 \u534f\u8bae\u3002");
        writeFile(docs.resolve("\u4f1a\u8bae\u8bb0\u5f55.txt"),
            "2026\u5e744\u670810\u65e5 \u4f1a\u8bae\u7eaa\u8981\n\n" +
            "1. \u8fdc\u7a0b\u6587\u4ef6\u7ba1\u7406\u5668\u9879\u76ee\u8fdb\u5c55\u987a\u5229\n" +
            "2. \u5df2\u5b8c\u6210 WebDAV \u534f\u8bae\u652f\u6301\n" +
            "3. \u5df2\u5b8c\u6210 SMB3 \u534f\u8bae\u652f\u6301");
        writeFile(docs.resolve("\u9879\u76ee\u8bf4\u660e.txt"),
            "Android \u8fdc\u7a0b\u6587\u4ef6\u7ba1\u7406\u5668\n\n" +
            "\u529f\u80fd\u7279\u6027:\n" +
            "- WebDAV \u534f\u8bae\u652f\u6301\n" +
            "- SMB3 \u534f\u8bae\u652f\u6301\n" +
            "- \u6587\u4ef6\u6d4f\u89c8\u4e0e\u7ba1\u7406\n" +
            "- \u5168\u4e2d\u6587\u754c\u9762");

        Path subDir = docs.resolve("\u5b50\u6587\u4ef6\u5939");
        Files.createDirectories(subDir);
        writeFile(subDir.resolve("\u5d4c\u5957\u6587\u4ef6.txt"),
            "\u8fd9\u662f\u5d4c\u5957\u5728\u5b50\u6587\u4ef6\u5939\u4e2d\u7684\u6587\u4ef6\u3002");

        writeFile(photos.resolve("\u98ce\u666f.txt"), "[\u98ce\u666f\u7167\u7247]");
        writeFile(photos.resolve("\u5408\u5f71.txt"), "[\u5408\u5f71\u7167\u7247]");
        writeFile(music.resolve("\u6b4c\u66f2\u5217\u8868.txt"), "1. \u6625\u98ce\u5341\u91cc\n2. \u590f\u591c\u665a\u98ce");
        writeFile(projects.resolve("\u9700\u6c42\u6587\u6863.txt"), "\u9879\u76ee\u9700\u6c42\u6587\u6863 v1.0\n\n\u6838\u5fc3\u529f\u80fd:\n1. \u6587\u4ef6\u6d4f\u89c8\n2. \u6587\u4ef6\u4e0a\u4f20\u4e0b\u8f7d\n3. \u6587\u4ef6\u5939\u7ba1\u7406");
        writeFile(projects.resolve("\u6280\u672f\u65b9\u6848.txt"), "\u6280\u672f\u67b6\u6784:\n- Kotlin + Jetpack Compose\n- OkHttp (WebDAV)\n- jcifs-ng (SMB3)\n- MVVM");
        writeFile(rootDir.resolve("\u6d4b\u8bd5\u6587\u4ef6.txt"), "\u8fd9\u662f\u4e00\u884c\u6d4b\u8bd5\u6570\u636e\uff0c\u7528\u4e8e\u9a8c\u8bc1\u6587\u4ef6\u8bfb\u53d6\u529f\u80fd\u3002");

        System.out.println("[OK] Demo data created");
    }

    static void writeFile(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    static class WebDAVHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"WebDAV\"");
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            String rawPath = exchange.getRequestURI().getPath();
            String requestPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
            Path targetPath = resolvePath(requestPath);
            try {
                switch (method) {
                    case "OPTIONS": handleOptions(exchange); break;
                    case "GET": case "HEAD": handleGet(exchange, targetPath, method.equals("HEAD")); break;
                    case "PUT": handlePut(exchange, targetPath); break;
                    case "DELETE": handleDelete(exchange, targetPath); break;
                    case "MKCOL": handleMkcol(exchange, targetPath); break;
                    case "PROPFIND": handlePropfind(exchange, targetPath, requestPath); break;
                    case "MOVE": handleMove(exchange, targetPath); break;
                    default: sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }

        private boolean checkAuth(HttpExchange exchange) {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.startsWith("Basic ")) return false;
            String decoded = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return parts.length == 2 && parts[0].equals(USERNAME) && parts[1].equals(PASSWORD);
        }

        private Path resolvePath(String requestPath) {
            String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
            if (relative.isEmpty()) return rootDir;
            return rootDir.resolve(relative).normalize();
        }

        private void handleOptions(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("DAV", "1");
            exchange.getResponseHeaders().set("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, MKCOL, PROPFIND, MOVE");
            exchange.sendResponseHeaders(200, -1);
        }

        private void handleGet(HttpExchange exchange, Path path, boolean headOnly) throws IOException {
            if (!Files.exists(path)) { sendResponse(exchange, 404, "Not Found"); return; }
            if (Files.isDirectory(path)) {
                StringBuilder html = new StringBuilder("<html><body><h1>").append(path.getFileName()).append("</h1><ul>");
                try (var stream = Files.list(path)) {
                    stream.sorted().forEach(p -> {
                        String n = p.getFileName().toString();
                        String s = Files.isDirectory(p) ? "/" : "";
                        html.append("<li><a href=\"").append(n).append(s).append("\">").append(n).append(s).append("</a></li>");
                    });
                }
                html.append("</ul></body></html>");
                byte[] bytes = html.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                if (!headOnly) { exchange.getResponseBody().write(bytes); }
                exchange.close();
            } else {
                byte[] bytes = Files.readAllBytes(path);
                exchange.getResponseHeaders().set("Content-Type", guessMime(path.getFileName().toString()));
                exchange.sendResponseHeaders(200, bytes.length);
                if (!headOnly) { exchange.getResponseBody().write(bytes); }
                exchange.close();
            }
        }

        private void handlePut(HttpExchange exchange, Path path) throws IOException {
            Files.createDirectories(path.getParent());
            try (InputStream is = exchange.getRequestBody()) {
                Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
            }
            sendResponse(exchange, 201, "Created");
        }

        private void handleDelete(HttpExchange exchange, Path path) throws IOException {
            if (!Files.exists(path)) { sendResponse(exchange, 404, "Not Found"); return; }
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
                    public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
                });
            } else { Files.delete(path); }
            sendResponse(exchange, 204, "");
        }

        private void handleMkcol(HttpExchange exchange, Path path) throws IOException {
            if (Files.exists(path)) { sendResponse(exchange, 409, "Conflict"); return; }
            Files.createDirectories(path);
            sendResponse(exchange, 201, "Created");
        }

        private void handlePropfind(HttpExchange exchange, Path path, String requestPath) throws IOException {
            if (!Files.exists(path)) { sendResponse(exchange, 404, "Not Found"); return; }

            String depth = exchange.getRequestHeaders().getFirst("Depth");
            if (depth == null) depth = "0";

            List<Path> targets = new ArrayList<>();
            targets.add(path);
            if ("1".equals(depth) && Files.isDirectory(path)) {
                try (var stream = Files.list(path)) { stream.sorted().forEach(targets::add); }
            }

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");

            for (Path p : targets) {
                boolean isDir = Files.isDirectory(p);
                String href = buildHref(p, requestPath);
                long size = isDir ? 0 : Files.size(p);
                String lastMod = formatDate(Files.getLastModifiedTime(p).toMillis());

                xml.append("  <D:response>\n");
                xml.append("    <D:href>").append(esc(href)).append("</D:href>\n");
                xml.append("    <D:propstat>\n");
                xml.append("      <D:prop>\n");
                xml.append("        <D:displayname>").append(esc(p.getFileName().toString())).append("</D:displayname>\n");
                if (isDir) {
                    xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
                } else {
                    xml.append("        <D:resourcetype/>\n");
                    xml.append("        <D:getcontenttype>").append(guessMime(p.getFileName().toString())).append("</D:getcontenttype>\n");
                }
                xml.append("        <D:getcontentlength>").append(size).append("</D:getcontentlength>\n");
                xml.append("        <D:getlastmodified>").append(lastMod).append("</D:getlastmodified>\n");
                xml.append("      </D:prop>\n");
                xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
                xml.append("    </D:propstat>\n");
                xml.append("  </D:response>\n");
            }
            xml.append("</D:multistatus>");

            byte[] bytes = xml.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=utf-8");
            exchange.sendResponseHeaders(207, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private void handleMove(HttpExchange exchange, Path source) throws IOException {
            if (!Files.exists(source)) { sendResponse(exchange, 404, "Not Found"); return; }
            String destHeader = exchange.getRequestHeaders().getFirst("Destination");
            if (destHeader == null) { sendResponse(exchange, 400, "Missing Destination"); return; }
            String destPath = URLDecoder.decode(java.net.URI.create(destHeader).getPath(), StandardCharsets.UTF_8);
            Path dest = resolvePath(destPath);
            Files.createDirectories(dest.getParent());
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            sendResponse(exchange, 201, "Created");
        }

        private String buildHref(Path p, String requestPath) {
            if (p.equals(rootDir)) return requestPath.endsWith("/") ? requestPath : requestPath + "/";
            String base = requestPath.endsWith("/") ? requestPath : requestPath + "/";
            return base + p.getFileName().toString() + (Files.isDirectory(p) ? "/" : "");
        }

        private String formatDate(long millis) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf.format(new Date(millis));
        }

        private String guessMime(String name) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            return switch (ext) {
                case "html","htm" -> "text/html";
                case "txt","log","md" -> "text/plain; charset=utf-8";
                case "json" -> "application/json";
                case "xml" -> "application/xml";
                case "jpg","jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "pdf" -> "application/pdf";
                case "mp3" -> "audio/mpeg";
                case "mp4" -> "video/mp4";
                default -> "application/octet-stream";
            };
        }

        private String esc(String s) {
            return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        }

        private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
