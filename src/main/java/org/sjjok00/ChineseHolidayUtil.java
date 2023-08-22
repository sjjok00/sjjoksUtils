package org.sjjok00;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中国法定节假日工具
 * <p>
 * 通过读取Apple日历的中国法定节假日订阅实现<br>
 * 默认使用Apple自带的订阅. 如果需要使用其他订阅, 可以调用{@code configure}方法传入订阅地址和节假日, 补班关键字
 *
 * @author sjjok00
 */
public class ChineseHolidayUtil {

    // 默认使用Apple日历的ICS订阅地址
    private static final String DEFAULT_ICS_URL = "https://calendars.icloud.com/holidays/cn_zh.ics/";
    // 默认假期关键字
    private static final String DEFAULT_KEY_WORD_HOLIDAY = "休";
    // 默认补班关键字
    private static final String DEFAULT_KEY_WORD_WORKDAY = "班";
    private static volatile ChineseHolidayUtil instance;
    // 工作日(周一~周五)放假的日期列表
    private final Set<LocalDate> holidayList = new HashSet<>();
    // 周末补班的日期列表
    private final Set<LocalDate> workdayList = new HashSet<>();
    // ICS订阅地址
    private String icsUrl;
    // 假期关键字
    private String keyWordHoliday;
    // 补班关键字
    private String keyWordWorkday;
    // 最近一次读取ICS文件的时间, 用于缓存
    private Date recentLoadTime;

    private ChineseHolidayUtil() {
    }

    public static void main(String[] args) throws IOException {
        ChineseHolidayUtil holidayUtil = getInstance();
        List<String> workdays = holidayUtil.getWorkdaysBetween("2023-09-28", "2023-10-15");
        System.out.println("工作日（包括补班）:" + workdays);
        List<String> holidays = holidayUtil.getHolidaysBetween("2023-09-28", "2023-10-15");
        System.out.println("节假日: " + holidays);
    }

    /**
     * 从给定的URL读取ICS文件的内容。
     *
     * @param url 要读取的ICS文件的URL
     * @return ICS文件的内容字符串
     * @throws IOException 如果读取文件时出错
     */
    private static String readICSFile(String url) throws IOException {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet httpGet = new HttpGet(url);
            HttpClientResponseHandler<String> handler = new BasicHttpClientResponseHandler();
            return httpClient.execute(httpGet, handler);
        }
    }

    /**
     * 返回ChineseHolidayUtil类的实例。<br/>默认使用Apple日历的ICS订阅地址和关键字。
     *
     * @return ChineseHolidayUtil类的实例。
     */
    public static ChineseHolidayUtil getInstance() {
        if (instance == null) {
            synchronized (ChineseHolidayUtil.class) {
                if (instance == null) {
                    instance = new ChineseHolidayUtil();
                }
            }
        }
        instance.icsUrl = DEFAULT_ICS_URL;
        instance.keyWordHoliday = DEFAULT_KEY_WORD_HOLIDAY;
        instance.keyWordWorkday = DEFAULT_KEY_WORD_WORKDAY;
        return instance;
    }

    /**
     * 自定义ICS订阅地址、假期/补班关键字。
     *
     * @param icsUrl         要配置的ICS文件的URL
     * @param keyWordHoliday 用于识别节假日的关键词
     * @param keyWordWorkday 用于识别工作日的关键词
     */
    public ChineseHolidayUtil configure(String icsUrl, String keyWordHoliday, String keyWordWorkday) {
        this.icsUrl = icsUrl;
        this.keyWordHoliday = keyWordHoliday;
        this.keyWordWorkday = keyWordWorkday;
        return this;
    }

    /**
     * 获取指定日期范围内的工作日列表。
     *
     * @param startDate 范围的起始日期，格式为"yyyy-MM-dd"
     * @param endDate   范围的结束日期，格式为"yyyy-MM-dd"
     * @return 指定日期范围内的工作日列表
     * @throws IOException 如果读取ICS文件时发生I/O错误
     */
    public List<String> getWorkdaysBetween(String startDate, String endDate) throws IOException {
        List<String> workdays = new ArrayList<>();

        checkHolidaysAndWorkdays();

        // 把startDate和endDate之间的工作日加到workdays. 判断条件: 如果在workdayList中, 或不在holidayList中且不是周末, 则为工作日
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (workdayList.contains(date) || (!holidayList.contains(date) && !(date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY))) {
                workdays.add(date.format(DateTimeFormatter.ISO_DATE));
            }
            date = date.plusDays(1);
        }
        return workdays;
    }

    /**
     * 获取指定日期范围内的节假日列表。
     *
     * @param startDate 范围的起始日期，格式为"yyyy-MM-dd"
     * @param endDate   范围的结束日期，格式为"yyyy-MM-dd"
     * @return 指定日期范围内的节假日列表
     * @throws IOException 如果读取ICS文件时发生I/O错误
     */
    public List<String> getHolidaysBetween(String startDate, String endDate) throws IOException {
        List<String> holidays = new ArrayList<>();

        checkHolidaysAndWorkdays();

        // 把startDate和endDate之间的节假日加到holidays. 判断条件: 如果在holidayList中, 或为周末且不在workdayList中, 则为工作日
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (holidayList.contains(date) || (!workdayList.contains(date) && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY))) {
                holidays.add(date.format(DateTimeFormatter.ISO_DATE));
            }
            date = date.plusDays(1);
        }
        return holidays;
    }

    /**
     * 检查节假日和工作日。如果过期了或为空, 则从ICS读取并缓存一天
     *
     * @throws IOException 如果发生I/O错误
     */

    private void checkHolidaysAndWorkdays() throws IOException {
        // 节假日和补班日期缓存一天, 避免频繁请求ICS订阅地址被封
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -1);
        Date yesterday = calendar.getTime();
        boolean isOutDated = recentLoadTime == null || recentLoadTime.before(yesterday);

        if (isOutDated || holidayList.isEmpty() || workdayList.isEmpty()) {
            String content = readICSFile(icsUrl);
            getHolidaysAndWorkdays(content);
            recentLoadTime = new Date();
        }
    }

    /**
     * 解析给定的内容并提取假期和工作日。
     *
     * @param content 要解析的内容
     */
    private void getHolidaysAndWorkdays(String content) {
        String[] parts = content.split("BEGIN:VEVENT");
        Pattern pattern4Start = Pattern.compile("DTSTART;VALUE=DATE:(\\d{4})(\\d{2})(\\d{2})");
        Pattern pattern4End = Pattern.compile("DTEND;VALUE=DATE:(\\d{4})(\\d{2})(\\d{2})");

        for (String part : parts) {
            if (part.contains("END:VEVENT")) {
                Matcher matcher = pattern4Start.matcher(part);
                while (matcher.find()) {
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    LocalDate startDate = LocalDate.of(year, month, day);

                    Matcher matcher4End = pattern4End.matcher(part);
                    if (matcher4End.find()) {
                        int endYear = Integer.parseInt(matcher4End.group(1));
                        int endMonth = Integer.parseInt(matcher4End.group(2));
                        int endDay = Integer.parseInt(matcher4End.group(3));
                        LocalDate endDate = LocalDate.of(endYear, endMonth, endDay);

                        if (part.contains(keyWordHoliday)) {
                            addDatesBetween(startDate, endDate, holidayList);
                        } else if (part.contains(keyWordWorkday)) {
                            addDatesBetween(startDate, endDate, workdayList);
                        }
                    } else {
                        if (part.contains(keyWordHoliday)) {
                            holidayList.add(startDate);
                        } else if (part.contains(keyWordWorkday)) {
                            workdayList.add(startDate);
                        }
                    }
                }
            }
        }
    }

    /**
     * 将startDate和endDate之间的日期加到dateSet中, [startDate, endDate)
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param dateSet   要添加到的日期集合
     */
    private void addDatesBetween(LocalDate startDate, LocalDate endDate, Set<LocalDate> dateSet) {
        LocalDate date = startDate;
        while (date.isBefore(endDate)) {
            dateSet.add(date);
            date = date.plusDays(1);
        }
    }
}
