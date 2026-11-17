import java.nio.file.*;
import at.heinz.deviceai.avatar3d.AvatarGlbContract;
public class ValidatePackagedAvatar {
 public static void main(String[] args) throws Exception {
  byte[] bytes=Files.readAllBytes(Path.of(args[0]));
  System.out.println("byte_count="+bytes.length);
  System.out.println("sha256="+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
  System.out.println(AvatarGlbContract.INSTANCE.validate(bytes));
 }
}
