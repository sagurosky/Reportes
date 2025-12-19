package plantilla.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
@Slf4j
public class Validadores {

    // 🔹 Helpers (los mismos que en tu ServicioImpl)
    public static String safeStringCell(Row row, int index) {
        try {
            if (row.getCell(index) == null) return null;
            return switch (row.getCell(index).getCellType()) {
                case STRING -> row.getCell(index).getStringCellValue().trim();
                case NUMERIC -> String.valueOf((long) row.getCell(index).getNumericCellValue());
                case FORMULA -> row.getCell(index).getStringCellValue();
                default -> null;
            };
        } catch (Exception e) {
            log.warn("⚠️ Error leyendo celda String col {}: {}", index, e.getMessage());
            return null;
        }
    }

    public static Integer safeIntCell(Row row, int index) {
        try {
            if (row.getCell(index) == null) return null;
            return switch (row.getCell(index).getCellType()) {
                case NUMERIC -> (int) row.getCell(index).getNumericCellValue();
                case STRING -> {
                    String val = row.getCell(index).getStringCellValue();
                    yield val.isBlank() ? null : Integer.parseInt(val.trim());
                }
                case FORMULA -> (int) row.getCell(index).getNumericCellValue();
                default -> null;
            };
        } catch (Exception e) {
            log.warn("⚠️ Error leyendo celda int col {}: {}", index, e.getMessage());
            return null;
        }
    }
    public static Long safeLongCell(Row row, int index) {
        try {
            if (row.getCell(index) == null) return null;

            return switch (row.getCell(index).getCellType()) {
                case NUMERIC -> (long) row.getCell(index).getNumericCellValue();
                case STRING -> {
                    String val = row.getCell(index).getStringCellValue();
                    yield val.isBlank() ? null : Long.parseLong(val.trim());
                }
                case FORMULA -> (long) row.getCell(index).getNumericCellValue();
                default -> null;
            };

        } catch (Exception e) {
            log.warn("⚠️ Error leyendo celda long col {}: {}", index, e.getMessage());
            return null;
        }
    }






}
