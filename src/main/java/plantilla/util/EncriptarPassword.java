package plantilla.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class EncriptarPassword {
    public static void main(String[] args) {
      //ejecutamos esta clase una vez para conseguir el encriptado del password
      //y ponerlo en la bbdd
        var password="123";
        System.out.println("password: "+password);
        System.out.println("password encriptado: "+encriptarPassword(password));
        
    }
    
    public static String encriptarPassword(String password){
          BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
          return encoder.encode(password);
    }
    
}
