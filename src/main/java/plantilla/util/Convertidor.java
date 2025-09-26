/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plantilla.util;

import java.time.LocalDateTime;

/**
 *
 * @author daniel
 */
public class Convertidor {
    
    
    
    public static String aCamelCase(String text){
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder camelCaseString = new StringBuilder();
        boolean nextCharUpperCase = false;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (Character.isWhitespace(currentChar) || currentChar == '_' || currentChar == '-') {
                nextCharUpperCase = true;
            } else {
                if (nextCharUpperCase) {
                    camelCaseString.append(Character.toUpperCase(currentChar));
                    nextCharUpperCase = false;
                } else {
                    camelCaseString.append(Character.toLowerCase(currentChar));
                }
            }
        }
        return camelCaseString.toString();
    }
    
    
    
    public static String deCamelCase(String camelCase) {
        // Convertir el primer carácter a mayúscula

//si necesito que el primer caracter sea mayuscula reemplazar por la siguiente linea
//        StringBuilder result = new StringBuilder(camelCase.substring(0, 1).toUpperCase());
        StringBuilder result = new StringBuilder(camelCase.substring(0, 1));

        // Recorrer el resto de la cadena
        for (int i = 1; i < camelCase.length(); i++) {
            char currentChar = camelCase.charAt(i);

            // Si el carácter actual es mayúscula, añadir un espacio y convertirlo a minúscula
            if (Character.isUpperCase(currentChar)||Character.isDigit(currentChar)) {
                result.append(" ");
                result.append(Character.toLowerCase(currentChar));
            } else {
                result.append(currentChar);
            }
        }

        return result.toString();
    }
    
    
    
    
    
}
