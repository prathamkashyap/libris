import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestBCrypt {
  public static void main(String[] args) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    String hash = "$2a$10$28EBLVVA9JQ9F.blKomw8eEM14eAE.GFr41L3vDfpmNzDIfLVsbnm";
    System.out.println("Admin@123 matches: " + encoder.matches("Admin@123", hash));
    System.out.println("ChangeMe123! matches: " + encoder.matches("ChangeMe123!", hash));
    System.out.println("admin123 matches: " + encoder.matches("admin123", hash));
    System.out.println("admin matches: " + encoder.matches("admin", hash));
  }
}
