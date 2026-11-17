import at.heinz.deviceai.avatar3d.AvatarGlbContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.json.JSONObject;

/** Independent launcher for the compiled production preflight and packaged pin. */
class ValidatePackagedAsset {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One packaged GLB path required");
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!sha.equals(AvatarGlbContract.ASSET_SHA256)) {
            throw new IllegalStateException("Packaged bytes differ from compiled production pin");
        }
        var metadata = AvatarGlbContract.INSTANCE.validate(bytes);
        if (metadata.getTargets().size() != 21) throw new IllegalStateException("Target contract changed");
        System.out.println(new JSONObject()
            .put("status", "PASS_COMPILED_PIN_AND_PRODUCTION_PREFLIGHT")
            .put("sha256", sha).put("bytes", bytes.length)
            .put("targetCount", metadata.getTargets().size())
            .put("evidenceClass", "CONTRACT_TEST_NOT_NATIVE_RENDER").toString(2));
    }
}
