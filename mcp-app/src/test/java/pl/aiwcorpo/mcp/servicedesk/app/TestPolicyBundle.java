package pl.aiwcorpo.mcp.servicedesk.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import pl.aiwcorpo.mcp.platform.test.PolicyRepository;

/**
 * The example policies of mcp-contracts (calls, reads, updates in scope, the service account rules of
 * {@code credential-type.cedar}, restricted data; unchanged) plus one group per test queue in the scope mapping, so the
 * grants of a caller can be told apart. Signed with the published test key, for tests only.
 */
final class TestPolicyBundle {

    static final String REVISION = "20261027100000-5d0b0b1";
    static final List<String> QUEUES = List.of("IT", "OPS", "SEC", "GONE", "FIN");

    private static final String EXAMPLES = "mcp-contracts/cedar/examples/";
    private static final List<String> EXAMPLE_POLICIES = List.of("call-tool.cedar", "credential-type.cedar",
            "forbid-restricted.cedar", "read.cedar", "step-up.cedar", "update.cedar");

    private TestPolicyBundle() {
    }

    static void publish(PolicyRepository repository) {
        try {
            Map<String, String> files = new TreeMap<>();
            files.put("manifest.json", "{\"revision\":\"" + REVISION + "\",\"gitSha\":\""
                    + "5d0b0b1".repeat(6).substring(0, 40) + "\",\"createdAt\":\"2026-10-27T10:00:00Z\","
                    + "\"schemaVersion\":\"3.1.0\"}");
            files.put("schema.cedarschema", text("mcp-contracts/cedar/mcp.cedarschema"));
            files.put("entities/static.json", groups());
            for (String policy : EXAMPLE_POLICIES) {
                files.put("policies/" + policy, text(EXAMPLES + policy));
            }
            byte[] archive = gzip(tar(files));

            String pem = text("mcp-contracts/test-vectors/keys/test-policy-bundle.NOT-FOR-PRODUCTION.private.pem")
                    .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))));
            signature.update(archive);
            repository.publishRaw(REVISION, archive, signature.sign());
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("cannot build the test bundle", ex);
        }
    }

    private static String groups() {
        StringBuilder json = new StringBuilder("[");
        for (String queue : QUEUES) {
            json.append(json.length() > 1 ? "," : "").append("{\"uid\":{\"type\":\"UserGroup\",\"id\":\"mcp-servicedesk-queue-")
                    .append(queue).append("\"},\"attrs\":{},\"parents\":[{\"type\":\"ResourceScope\",\"id\":\"ServiceDesk::Queue/")
                    .append(queue).append("\"}]}");
        }
        return json.append(']').toString();
    }

    private static String text(String resource) throws IOException {
        try (InputStream in = TestPolicyBundle.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException(resource + " not found on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] tar(Map<String, String> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Map.Entry<String, String> file : files.entrySet()) {
                byte[] content = file.getValue().getBytes(StandardCharsets.UTF_8);
                byte[] header = new byte[512];
                put(header, 0, file.getKey());
                put(header, 100, "0000644");
                put(header, 108, "0000000");
                put(header, 116, "0000000");
                put(header, 124, String.format("%011o", content.length));
                put(header, 136, "00000000000");
                Arrays.fill(header, 148, 156, (byte) ' ');
                header[156] = '0';
                put(header, 257, "ustar");
                put(header, 263, "00");
                long sum = 0;
                for (byte b : header) {
                    sum += b & 0xff;
                }
                put(header, 148, String.format("%06o", sum));
                header[154] = 0;
                header[155] = ' ';
                out.write(header);
                out.write(content);
                out.write(new byte[(512 - content.length % 512) % 512]);
            }
            out.write(new byte[1024]);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void put(byte[] header, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, bytes.length);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }
}
