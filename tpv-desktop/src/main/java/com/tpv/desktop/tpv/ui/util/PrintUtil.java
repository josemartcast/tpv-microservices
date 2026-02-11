package com.tpv.desktop.tpv.ui.util;

import java.util.Locale;
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
    private PrintUtil() {
    }

    public static void printTextToPdf(String text, Window owner) {
        Printer printer = findPdfPrinter();
        if (printer == null) {
            throw new RuntimeException("No se encontro impresora PDF (Microsoft Print to PDF).");
        }

        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new RuntimeException("No se pudo crear trabajo de impresion.");
        }

        Text printableText = new Text(text == null ? "" : text);
        printableText.setFont(Font.font("Consolas", 11));
        printableText.wrappingWidthProperty().set(540);
        VBox printableRoot = new VBox(printableText);
        printableRoot.setPadding(new Insets(16));

        PageLayout pageLayout = printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        job.getJobSettings().setPageLayout(pageLayout);

        boolean accepted = owner == null || job.showPrintDialog(owner);
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
}

