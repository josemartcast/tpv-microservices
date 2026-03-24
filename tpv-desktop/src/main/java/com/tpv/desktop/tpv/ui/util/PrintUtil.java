package com.tpv.desktop.tpv.ui.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javafx.geometry.Insets;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Window;

public final class PrintUtil {
    private static final double MM_PER_POINT = 25.4 / 72.0;
    private static final double THERMAL_TARGET_MM = 80.0;
    private static final double DEFAULT_FONT_SIZE = 10.0;
    private static final double MIN_FONT_SIZE = 7.0;
    private static final int BOTTOM_MARGIN_LINES = 5;
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;

    private PrintUtil() {
    }

    public static void printTextToPdf(String text, Window owner) {
        Printer printer = findPdfPrinter();
        if (printer == null) {
            throw new RuntimeException("No se encontro impresora PDF (Microsoft Print to PDF).");
        }
        printText(text, printer, owner, false, false, false);
    }

    public static void printTextToPdfWithBottomMargin(String text, Window owner) {
        Printer printer = findPdfPrinter();
        if (printer == null) {
            throw new RuntimeException("No se encontro impresora PDF (Microsoft Print to PDF).");
        }
        printText(text, printer, owner, false, true, false);
    }

    public static void printTextToPdfEmphasized(String text, Window owner) {
        Printer printer = findPdfPrinter();
        if (printer == null) {
            throw new RuntimeException("No se encontro impresora PDF (Microsoft Print to PDF).");
        }
        printText(text, printer, owner, false, false, true);
    }

    public static void printTextToPrinter(String printerName, String text, Window owner) {
        Printer printer = findPrinterByName(printerName);
        if (printer == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }
        printText(text, printer, owner, false, false, false);
    }

    public static void printTextToPrinterEmphasized(String printerName, String text, Window owner) {
        Printer printer = findPrinterByName(printerName);
        if (printer == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }
        printText(text, printer, owner, false, false, true);
    }

    public static void printTextToPrinterWithBottomMargin(String printerName, String text, Window owner) {
        Printer printer = findPrinterByName(printerName);
        if (printer == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }
        printText(text, printer, owner, false, true, false);
    }

    public static void openCashDrawer(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            throw new RuntimeException("Impresora no configurada.");
        }

        PrintService service = findPrintServiceByName(printerName);
        if (service == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }

        // ESC/POS: ESC p m t1 t2  -> pulso para cajon (m=0).
        byte[] openDrawer = new byte[] {0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(openDrawer, flavor, null);
        DocPrintJob job = service.createPrintJob();
        try {
            job.print(doc, null);
        } catch (PrintException e) {
            throw new RuntimeException("Error enviando senal al cajon: " + e.getMessage(), e);
        }
    }

    public static List<String> availablePrinterNames() {
        List<String> out = new ArrayList<>();
        for (Printer printer : Printer.getAllPrinters()) {
            if (printer == null || printer.getName() == null || printer.getName().isBlank()) {
                continue;
            }
            out.add(printer.getName().trim());
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static void printText(
            String text,
            Printer printer,
            Window owner,
            boolean showDialog,
            boolean addBottomMargin,
            boolean emphasized
    ) {
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new RuntimeException("No se pudo crear trabajo de impresion.");
        }
        String printableContent = text == null ? "" : text;
        if (addBottomMargin) {
            printableContent = appendBottomMargin(printableContent, BOTTOM_MARGIN_LINES);
        }

        PageLayout pageLayout = printer.createPageLayout(
                selectBestPaper(printer),
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );
        double printableWidth = pageLayout.getPrintableWidth();
        double fontSize = resolveFontSizeForWidth(text, printableWidth > 0 ? printableWidth : 280);
        if (emphasized) {
            fontSize = Math.min(fontSize + 1.5, DEFAULT_FONT_SIZE + 3.0);
        }
        Font font = Font.font("Monospaced", FontWeight.BOLD, fontSize);
        job.getJobSettings().setPageLayout(pageLayout);

        boolean accepted = !showDialog || owner == null || job.showPrintDialog(owner);
        if (!accepted) {
            return;
        }

        int linesPerPage = resolveLinesPerPage(font, pageLayout.getPrintableHeight());
        List<String> pages = splitIntoPages(printableContent, linesPerPage);
        boolean pdfLikePrinter = isPdfLikePrinter(printer.getName());
        if (!pdfLikePrinter && pages.size() > 1) {
            printAsSingleTextJob(printer.getName(), printableContent);
            return;
        }
        for (String pageText : pages) {
            Text printableText = new Text(pageText);
            printableText.setFont(font);
            printableText.setWrappingWidth(0); // El contenido ya viene envuelto/formateado por columnas.
            VBox printableRoot = new VBox(printableText);
            printableRoot.setPadding(new Insets(2, 0, 2, 0));
            boolean printed = job.printPage(pageLayout, printableRoot);
            if (!printed) {
                job.cancelJob();
                throw new RuntimeException("Fallo al imprimir ticket completo.");
            }
        }
        job.endJob();
    }

    private static int resolveLinesPerPage(Font font, double printableHeight) {
        if (printableHeight <= 0) {
            return 48;
        }
        Text probe = new Text("Ag");
        probe.setFont(font);
        double lineHeight = Math.max(1.0, probe.getLayoutBounds().getHeight() + 1.0);
        int lines = (int) Math.floor((printableHeight - 2.0) / lineHeight);
        return Math.max(1, lines);
    }

    private static List<String> splitIntoPages(String text, int linesPerPage) {
        String normalized = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (linesPerPage <= 0 || lines.length <= linesPerPage) {
            return List.of(normalized);
        }

        List<String> pages = new ArrayList<>();
        for (int start = 0; start < lines.length; start += linesPerPage) {
            int end = Math.min(start + linesPerPage, lines.length);
            pages.add(String.join("\n", Arrays.copyOfRange(lines, start, end)));
        }
        return pages;
    }

    private static void printAsSingleTextJob(String printerName, String text) {
        PrintService service = findPrintServiceByName(printerName);
        if (service == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        byte[] payload = buildEscPosPayload(text == null ? "" : text);
        Doc doc = new SimpleDoc(payload, flavor, null);
        DocPrintJob job = service.createPrintJob();
        try {
            job.print(doc, null);
        } catch (PrintException e) {
            throw new RuntimeException("Error imprimiendo ticket largo en modo continuo: " + e.getMessage(), e);
        }
    }

    private static byte[] buildEscPosPayload(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // ESC/POS init + estilo base para evitar que quede una fuente "estrecha" persistente.
            out.write(new byte[] {ESC, '@'});            // Initialize printer
            out.write(new byte[] {ESC, 'a', 0x00});      // Align left
            out.write(new byte[] {ESC, 'M', 0x00});      // Font A (normal)
            out.write(new byte[] {ESC, '!', 0x00});      // Text mode normal
            out.write(new byte[] {GS, '!', 0x00});       // Character size normal
            out.write(0x12);                              // DC2 -> cancela modo condensado
            out.write(new byte[] {ESC, 't', 0x10});      // Code page CP1252 (usual en ES)

            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            out.write(normalized.getBytes(StandardCharsets.ISO_8859_1));

            // Margen inferior + corte final (una sola vez).
            out.write('\n');
            out.write('\n');
            out.write('\n');
            out.write('\n');
            out.write('\n');
            out.write(new byte[] {GS, 'V', 0x42, 0x00}); // Partial cut
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir payload ESC/POS: " + e.getMessage(), e);
        }
    }

    private static String appendBottomMargin(String text, int blankLines) {
        if (blankLines <= 0) {
            return text == null ? "" : text;
        }
        String base = text == null ? "" : text;
        StringBuilder out = new StringBuilder(base);
        for (int i = 0; i < blankLines; i++) {
            out.append('\n');
        }
        return out.toString();
    }

    private static Printer findPrinterByName(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return null;
        }
        String target = printerName.trim().toLowerCase(Locale.ROOT);
        for (Printer printer : Printer.getAllPrinters()) {
            String current = printer.getName();
            if (current == null) {
                continue;
            }
            if (current.trim().toLowerCase(Locale.ROOT).equals(target)) {
                return printer;
            }
        }
        return null;
    }

    private static Paper selectBestPaper(Printer printer) {
        if (printer == null) {
            return Paper.A4;
        }
        Set<Paper> supported = printer.getPrinterAttributes().getSupportedPapers();
        if (supported == null || supported.isEmpty()) {
            return printer.getPrinterAttributes().getDefaultPaper();
        }

        Paper best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Paper paper : supported) {
            String name = paper.getName() == null ? "" : paper.getName().toLowerCase(Locale.ROOT);
            double widthMm = paper.getWidth() * MM_PER_POINT;
            double heightMm = paper.getHeight() * MM_PER_POINT;
            double shortSideMm = Math.min(widthMm, heightMm);

            boolean likelyReceiptByName =
                    name.contains("80")
                            || name.contains("receipt")
                            || name.contains("roll")
                            || name.contains("ticket")
                            || name.contains("pos");

            if (likelyReceiptByName && shortSideMm >= 60 && shortSideMm <= 90) {
                return paper;
            }

            double distance = Math.abs(shortSideMm - THERMAL_TARGET_MM);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = paper;
            }
        }

        if (best != null && bestDistance <= 12.0) {
            return best;
        }
        return printer.getPrinterAttributes().getDefaultPaper();
    }

    private static Printer findPdfPrinter() {
        for (Printer printer : Printer.getAllPrinters()) {
            String name = printer.getName();
            if (name == null) continue;
            if (isPdfLikePrinter(name)) {
                return printer;
            }
        }
        return null;
    }

    private static boolean isPdfLikePrinter(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return false;
        }
        String n = printerName.toLowerCase(Locale.ROOT);
        return n.contains("microsoft print to pdf") || n.contains("print to pdf");
    }

    private static PrintService findPrintServiceByName(String printerName) {
        String target = printerName.trim().toLowerCase(Locale.ROOT);
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (service == null || service.getName() == null) {
                continue;
            }
            if (service.getName().trim().toLowerCase(Locale.ROOT).equals(target)) {
                return service;
            }
        }
        return null;
    }

    private static double resolveFontSizeForWidth(String text, double printableWidth) {
        int longestLine = longestLineLength(text);
        if (longestLine <= 0 || printableWidth <= 0) {
            return DEFAULT_FONT_SIZE;
        }

        double candidate = DEFAULT_FONT_SIZE;
        while (candidate >= MIN_FONT_SIZE) {
            Text probe = new Text("X".repeat(longestLine));
            probe.setFont(Font.font("Monospaced", FontWeight.BOLD, candidate));
            double probeWidth = probe.getLayoutBounds().getWidth();
            if (probeWidth <= printableWidth - 2) {
                return candidate;
            }
            candidate -= 0.5;
        }
        return MIN_FONT_SIZE;
    }

    private static int longestLineLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int max = 0;
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (String line : lines) {
            if (line.length() > max) {
                max = line.length();
            }
        }
        return max;
    }

}
