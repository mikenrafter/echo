package eu.mrogalski.saidit.util;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates filenames from patterns with placeholder substitution.
 * 
 * Supported placeholders:
 * 
 * Date components:
 * - {YYYY} = 4-digit year (e.g., 2026)
 * - {YY} = 2-digit year (e.g., 26)
 * - {MM} = 2-digit month (01-12)
 * - {DD} = 2-digit day (01-31)
 * - {M} = Month without leading zero (1-12)
 * - {D} = Day without leading zero (1-31)
 * 
 * Time components:
 * - {HH} = 2-digit hour 24-hour format (00-23)
 * - {hh} = 2-digit hour 12-hour format (01-12)
 * - {mm} = 2-digit minute (00-59)
 * - {ss} = 2-digit second (00-59)
 * - {H} = Hour without leading zero (0-23)
 * - {h} = Hour 12-hour without leading zero (1-12)
 * - {m} = Minute without leading zero (0-59)
 * - {s} = Second without leading zero (0-59)
 * - {A} = AM/PM indicator
 * - {a} = am/pm indicator
 * 
 * Other:
 * - {timestamp} = Unix timestamp
 * - {duration} = Duration in minutes
 * - {counter} = Sequential counter (0001, 0002, ...)
 * - {type} = "Auto" or "Manual"
 */
public class FilenamePatternGenerator {
    
    private static final AtomicInteger counter = new AtomicInteger(1);
    
    /**
     * Default pattern for auto-saves
     */
    public static final String DEFAULT_PATTERN = "Auto-save_{YYYY}-{MM}-{DD}_{HH}-{mm}-{ss}";
    
    /**
     * Generate a filename from a pattern.
     * 
     * @param pattern The pattern string with placeholders
     * @param durationSeconds Duration of the recording in seconds (optional, use 0 if not applicable)
     * @param isAutoSave Whether this is an auto-save (true) or manual save (false)
     * @return The generated filename (without extension)
     */
    public static String generate(String pattern, int durationSeconds, boolean isAutoSave) {
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = DEFAULT_PATTERN;
        }
        
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        
        String result = pattern;
        
        // Year
        result = result.replace("{YYYY}", String.format(Locale.US, "%04d", cal.get(Calendar.YEAR)));
        result = result.replace("{YY}", String.format(Locale.US, "%02d", cal.get(Calendar.YEAR) % 100));
        
        // Month
        int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        result = result.replace("{MM}", String.format(Locale.US, "%02d", month));
        result = result.replace("{M}", String.valueOf(month));
        
        // Day
        int day = cal.get(Calendar.DAY_OF_MONTH);
        result = result.replace("{DD}", String.format(Locale.US, "%02d", day));
        result = result.replace("{D}", String.valueOf(day));
        
        // Hour (24-hour)
        int hour24 = cal.get(Calendar.HOUR_OF_DAY);
        result = result.replace("{HH}", String.format(Locale.US, "%02d", hour24));
        result = result.replace("{H}", String.valueOf(hour24));
        
        // Hour (12-hour)
        int hour12 = cal.get(Calendar.HOUR);
        if (hour12 == 0) hour12 = 12; // 0 should be 12 in 12-hour format
        result = result.replace("{hh}", String.format(Locale.US, "%02d", hour12));
        result = result.replace("{h}", String.valueOf(hour12));
        
        // Minute
        int minute = cal.get(Calendar.MINUTE);
        result = result.replace("{mm}", String.format(Locale.US, "%02d", minute));
        result = result.replace("{m}", String.valueOf(minute));
        
        // Second
        int second = cal.get(Calendar.SECOND);
        result = result.replace("{ss}", String.format(Locale.US, "%02d", second));
        result = result.replace("{s}", String.valueOf(second));
        
        // AM/PM
        int amPm = cal.get(Calendar.AM_PM);
        result = result.replace("{A}", amPm == Calendar.AM ? "AM" : "PM");
        result = result.replace("{a}", amPm == Calendar.AM ? "am" : "pm");
        
        // Timestamp
        result = result.replace("{timestamp}", String.valueOf(now.getTime() / 1000));
        
        // Duration in minutes
        int minutes = durationSeconds / 60;
        result = result.replace("{duration}", String.valueOf(minutes));
        
        // Counter
        result = result.replace("{counter}", String.format(Locale.US, "%04d", counter.getAndIncrement()));
        
        // Type
        result = result.replace("{type}", isAutoSave ? "Auto" : "Manual");
        
        // Sanitize filename - remove invalid characters but preserve structure
        result = result.replaceAll("[<>:\"|?*]", "_");
        
        return result;
    }
    
    /**
     * Generate a preview of what the pattern will produce.
     * Uses current time and example values.
     */
    public static String generatePreview(String pattern) {
        return generate(pattern, 300, true) + ".m4a";
    }
    
    /**
     * Reset the counter (useful for testing)
     */
    public static void resetCounter() {
        counter.set(1);
    }
}
