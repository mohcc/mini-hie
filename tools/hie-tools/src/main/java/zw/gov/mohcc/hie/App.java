package zw.gov.mohcc.hie;

import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxDumper;
import com.github.odiszapc.nginxparser.NgxParam;
import io.github.atkawa7.caseutils.Style;
import io.github.atkawa7.haproxy.Config;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.codec.digest.Sha2Crypt;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.StringTemplate.STR;

/**
 * Hello world!
 *
 */
public class App {

    public static List<String> serverNames(String domain, List<String> domainSuffix) {
        return domainSuffix.stream().map(s -> domain + s).toList();
    }

//    public static List<ServerConfig> createConfig(List<String> domainSuffix) {
//        List<ServerConfig> serverConfigList = new ArrayList<>();
//        serverConfigList.add(ServerConfig.builder().name("comms.openhim").serverNames(serverNames("comms.openhim", domainSuffix)).host("nodeapp1").port("8081").build());
//        serverConfigList.add(ServerConfig.builder().name("core.openhim").serverNames(serverNames("core.openhim", domainSuffix)).host("nodeapp2").port("8082").build());
//        serverConfigList.add(ServerConfig.builder().name("openhim").serverNames(serverNames("openhim", domainSuffix)).host("nodeapp3").port("8083").build());
//        serverConfigList.add(ServerConfig.builder().name("opencr").serverNames(serverNames("opencr", domainSuffix)).host("nodeapp4").protocol("https").port("8084").build());
//        serverConfigList.add(ServerConfig.builder().name("gofr").serverNames(serverNames("gofr", domainSuffix)).host("nodeapp5").port("8085").build());
//        serverConfigList.sort(Comparator.comparing(ServerConfig::getName));
//        return serverConfigList;
//    }

    public static List<ServerConfig> createConfig(List<String> domainSuffix){
        List<ServerConfig> serverConfigList = new ArrayList<>();
        serverConfigList.add(ServerConfig.builder().name("comms.openhim").serverNames(serverNames("comms.openhim", domainSuffix)).host("ihe-openhim-core").port("8080").build());
        serverConfigList.add(ServerConfig.builder().name("core.openhim").serverNames(serverNames("core.openhim", domainSuffix)).host("ihe-openhim-core").port("5000").build());
        serverConfigList.add(ServerConfig.builder().name("openhim").serverNames(serverNames("openhim", domainSuffix)).host("ihe-openhim-console").port("80").build());
        serverConfigList.add(ServerConfig.builder().name("opencr").serverNames(serverNames("opencr", domainSuffix)).host("opencr").protocol("https").port("3000").build());
        serverConfigList.add(ServerConfig.builder().name("gofr").serverNames(serverNames("gofr", domainSuffix)).host("gofr").port("4000").build());
        serverConfigList.sort(Comparator.comparing(ServerConfig::getName));
        return serverConfigList;
    }

    public static String createHAProxy(Config config, List<ServerConfig> configs) {
        //frontend stats
        //    bind *:8404
        //    stats enable
        //    stats uri /
        //    stats refresh 10s
        config
                .defaults()
                .mode("http")
                .timeout("client", "10s")
                .timeout("connect", "5s")
                .timeout("server", "10s")
                .timeout("http-request", "10s")
                .log("global");
        final var frontend = config.frontend("default")
                .bind("*:80")
                .param(StringUtils.EMPTY)
                .stats("enable")
                .stats("refresh", "10s")
                .stats("uri", "/")
                .acl("acl_haproxy", "req.hdr(Host)", "-i", "haproxy.zhie.local")
                .stats("admin", "if", "acl_haproxy")
                .stats("auth", "username:password")
                .param(StringUtils.EMPTY);


        for (int i = 0; i < configs.size(); i++) {
            if (i > 0) {
                frontend.param(StringUtils.EMPTY);
            }
            final var serverConfig = configs.get(i);
            final String transform = Style.SNAKE_CASE.transform(serverConfig.getName());
            final var aclName = STR."acl_\{transform}";
            for (String serverName : serverConfig.getServerNames()) {
                frontend.acl(aclName, STR."req.hdr(Host) -i \{serverName}");
            }
            final var backend = STR."be_\{transform}";
            frontend.useBackend(backend, "if", aclName);

        }

        for (ServerConfig serverConfig : configs) {
            final var name = Style.SNAKE_CASE.transform(serverConfig.getName());
            final var backend = config
                    .backend(STR."be_\{name}")
                    .mode("http")
                    .option("forwardfor")
//                    .option("httpclose")
//                    .option ("forwardfor")

                    .httpRequest("set-header", "host", serverConfig.getServerNames().getFirst());

            if (serverConfig.isHttps()) {
                backend.server(name, STR."\{serverConfig.getHost()}:\{serverConfig.getPort()} crt /usr/local/etc/haproxy/certs/\{serverConfig.getPem()} ssl verify none");
            } else {
                backend.server(name, STR."\{serverConfig.getHost()}:\{serverConfig.getPort()}");
            }
        }
        return config.toString();
    }

    public static NgxBlock createNgxStatus(String domainSuffix){
        final var serverBlock = new NgxBlock();
        serverBlock.addValue("server");

        final var serverTokens = new ArrayList<String>();
        serverTokens.add("listen 80");
        serverTokens.add(STR."server_name  nginx\{domainSuffix}");

        for (final var serverToken : serverTokens) {
            final var entry = new NgxParam();
            entry.addValue(serverToken);
            serverBlock.addEntry(entry);
        }

        final var rootBlock = new NgxBlock();
        rootBlock.addValue("location /");

        final var tokens = new ArrayList<String>();
        tokens.add("stub_status");
        tokens.add("auth_basic \"Restricted Content\"");
        tokens.add("auth_basic_user_file /etc/nginx/.htpasswd");
//        tokens.add(STR."allow nginx\{domainSuffix}");
//        tokens.add("deny all");





        for (final var token : tokens) {
            final var entry = new NgxParam();
            entry.addValue(token);
            rootBlock.addEntry(entry);
        }
        serverBlock.addEntry(rootBlock);
        return serverBlock;
    }
    public static NgxBlock createNgxConfig(ServerConfig serverConfig) {
        Objects.requireNonNull(serverConfig);
        final String serverName = String.join(" ", serverConfig.getServerNames());
        final String host = serverConfig.getHost();
        final String port = serverConfig.getPort();
        final String protocol = StringUtils.defaultIfBlank(serverConfig.getProtocol(), "http");
        final var serverBlock = new NgxBlock();
        serverBlock.addValue("server");

        final var serverTokens = new ArrayList<String>();
        serverTokens.add("listen 80");
        serverTokens.add(STR."server_name \{serverName}");

        for (final var serverToken : serverTokens) {
            final var entry = new NgxParam();
            entry.addValue(serverToken);
            serverBlock.addEntry(entry);
        }

        final var rootBlock = new NgxBlock();
        rootBlock.addValue("location /");

        final var tokens = new ArrayList<String>();
        tokens.add("proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for");
        tokens.add("proxy_set_header X-Forwarded-Proto $scheme");
        tokens.add("proxy_set_header X-Real-IP $remote_addr");

        tokens.add("proxy_set_header Host $http_host");
        tokens.add("proxy_set_header Authorization $http_authorization");
        tokens.add("proxy_pass_header Authorization");
        tokens.add(STR."proxy_pass \{protocol}://\{host}:\{port}");
        tokens.add("proxy_redirect off");


        if (serverConfig.isHttps()) {
            tokens.add("proxy_ssl_verify off");
            tokens.add(STR."proxy_ssl_certificate /etc/nginx/certs/\{serverConfig.getCert()}");
            tokens.add(STR."proxy_ssl_certificate_key /etc/nginx/certs/\{serverConfig.getKey()}");
        }


        for (final var token : tokens) {
            final var entry = new NgxParam();
            entry.addValue(token);
            rootBlock.addEntry(entry);
        }
        serverBlock.addEntry(rootBlock);


        return serverBlock;
    }

    public static void main(String[] args) throws IOException {
        final var config = new NgxConfig();
        final var domainSuffix = List.of(".zhie.local");
        final var httpBlock = new NgxBlock();
        httpBlock.addValue("http");

        final var entry = new NgxParam();
        entry.addValue("worker_processes 1");
        final var eventEntry = new NgxParam();
        eventEntry.addValue("worker_connections 1024");
        final var events = new NgxBlock();
        events.addValue("events");
        events.addEntry(eventEntry);
        config.addEntry(entry);

        List<ServerConfig> serverConfigs = createConfig(domainSuffix);
        for (final NgxBlock ngxEntries : serverConfigs.stream()
                .map(App::createNgxConfig).toList()) {

            httpBlock.addEntry(ngxEntries);
        }
        httpBlock.addEntry(createNgxStatus(domainSuffix.getFirst()));
        config.addEntry(events);
        config.addEntry(httpBlock);

        final var servers = new ArrayList<String>();
        for (final var serverConfig : serverConfigs) {
            String first = serverConfig.getServerNames().getFirst();
            servers.add(first);
        }
        servers.add(STR."haproxy\{domainSuffix.getFirst()}");
        servers.add(STR."nginx\{domainSuffix.getFirst()}");
        servers.add(STR."spark\{domainSuffix.getFirst()}");


        final var dir = Paths.get("../../local/conf").toFile();
        final var certs = Paths.get("../../local/certs").toFile();



        try{
            dir.mkdir();
            certs.mkdir();
        }
        catch (Exception ex){
            throw new IllegalArgumentException(ex);
        }


        final var etcHost = servers.stream().map(s -> STR."127.0.0.1 \{s}").collect(Collectors.joining("\n"));
        FileUtils.write(new File( dir,"hosts"), etcHost, StandardCharsets.UTF_8);
        FileUtils.write(new File(dir, "nginx.conf"), new NgxDumper(config).dump(), StandardCharsets.UTF_8);
        FileUtils.write(new File(dir,"haproxy.cfg"),
                createHAProxy(new Config(), serverConfigs) + StringUtils.LF,
                StandardCharsets.UTF_8);


        final String username  = "username";
        final String password  = "password";
        final String apr1Crypt = Md5Crypt.apr1Crypt(password.getBytes());
        final String value  = STR."\{username}:\{apr1Crypt}";

        FileUtils.write(new File(dir, ".htpasswd"),
                value + StringUtils.LF,
                StandardCharsets.UTF_8);
        System.out.println(Sha2Crypt.sha512Crypt("1234567".getBytes(StandardCharsets.UTF_8)));

        final var urls  = List.of(
                "https://raw.githubusercontent.com/intrahealth/client-registry/master/server/clientCertificates/openmrs_cert.pem",
                "https://raw.githubusercontent.com/intrahealth/client-registry/master/server/clientCertificates/openmrs_key.pem");
        final var names  = List.of("opencr.zhie.local.crt", "opencr.zhie.local.key"); //Keep order pem =  cert + key
        final var pem = new File(certs, "opencr.zhie.local.pem");
        for (int i = 0; i < urls.size(); i++) {
            final var url  = urls.get(i);

            final var name  = FilenameUtils.getName(url);
            final var file  = new File(certs, name);
            if(!file.exists()){
                FileUtils.copyURLToFile(URI.create(url).toURL(), file);
            }
            final var localName  = new File(certs, names.get(i));
            if(!localName.exists() && file.exists()){
                file.renameTo(localName);
            }
            String source = FileUtils.readFileToString(localName,  StandardCharsets.UTF_8);
            if( i  == 0){
                FileUtils.write(pem, source, StandardCharsets.UTF_8);
            } else {
                FileUtils.write(pem, source, StandardCharsets.UTF_8, true);
            }
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServerConfig {
        private List<String> serverNames;
        private String host;
        private String port;
        private String protocol;
        private String name;

        public boolean isHttps() {
            return StringUtils.equalsIgnoreCase(protocol, "https");
        }

        public String getCert() {
            return STR."\{getServerNames().getFirst()}.crt";
        }

        public String getKey() {
            return STR."\{getServerNames().getFirst()}.key";
        }

        public String getPem() {
            return STR."\{getServerNames().getFirst()}.pem";
        }
    }
}
