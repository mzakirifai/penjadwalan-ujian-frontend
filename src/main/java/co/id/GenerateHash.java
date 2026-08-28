package co.id;

import org.mindrot.jbcrypt.BCrypt;

public class GenerateHash {
    public static void main(String[] args) {
        String plainPassword = "dewi123";
        String hashed = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        System.out.println("Password asli: " + plainPassword);
        System.out.println("Password ter-hash: " + hashed);
    }
}