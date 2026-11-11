import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** 운영/로컬 발급 계정의 BCrypt 해시 생성. 비밀번호는 콘솔에서 숨겨서 입력한다. */
public class StaffPasswordHash {
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("IntelliJ Terminal 또는 PowerShell에서 직접 실행하세요.");
        char[] input = console.readPassword("Staff password: ");
        if (input == null) throw new IllegalArgumentException("비밀번호 입력이 취소되었습니다.");
        try {
            String password = new String(input);
            if (password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("비밀번호는 비어 있지 않은 UTF-8 72바이트 이하여야 합니다.");
            }
            System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        } finally {
            Arrays.fill(input, '\0');
        }
    }
}
