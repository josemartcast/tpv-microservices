package com.tpv.desktop.tpv.ui.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import javafx.scene.text.Text;
import javafx.stage.Window;

public final class PrintUtil {
    private static final double MM_PER_POINT = 25.4 / 72.0;
    private static final double THERMAL_TARGET_MM = 80.0;

    private PrintUtil() {
    }

    public static void printTextToPdf(String text, Window owner) {
        Printer printer = findPdfPrinter();
        if (printer == null) {
            throw new RuntimeException("No se encontro impresora PDF (Microsoft Print to PDF).");
        }
        printText(text, printer, owner, false);
    }

    public static void printTextToPrinter(String printerName, String text, Window owner) {
        Printer printer = findPrinterByName(printerName);
        if (printer == null) {
            throw new RuntimeException("No se encontro la impresora: " + printerName);
        }
        printText(text, printer, owner, false);
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

    private static void printText(String text, Printer printer, Window owner, boolean showDialog) {
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new RuntimeException("No se pudo crear trabajo de impresion.");
        }

        Text printableText = new Text(text == null ? "" : text);
        printableText.setFont(Font.font("Consolas", 11));
        VBox printableRoot = new VBox(printableText);
        printableRoot.setPadding(new Insets(4));

        PageLayout pageLayout = printer.createPageLayout(
                selectBestPaper(printer),
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );
        double printableWidth = pageLayout.getPrintableWidth();
        printableText.wrappingWidthProperty().set(printableWidth > 0 ? printableWidth : 280);
        job.getJobSettings().setPageLayout(pageLayout);

        boolean accepted = !showDialog || owner == null || job.showPrintDialog(owner);
        if (!accepted) {
            return;
        }

        boolean printed = job.printPage(pageLayout, printableRoot);
        if (!printed) {
            job.cancelJob();
            throw new RuntimeException("Fallo al imprimir en PDF.");
        }
        job.endJob();
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
            String n = name.toLowerCase(Locale.ROOT);
            if (n.contains("microsoft print to pdf") || n.contains("print to pdf")) {
                return printer;
            }
        }
        return null;
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
}
