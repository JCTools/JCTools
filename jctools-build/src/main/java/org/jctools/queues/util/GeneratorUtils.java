package org.jctools.queues.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileWriter;

public class GeneratorUtils {

    /**
     * Remove padding fields from the given class.
     */
    public static void cleanupPaddingComments(CompilationUnit cu)
    {
        for (Comment comment : cu.getAllContainedComments())
        {
            String content = comment.getContent();
            if (content.contains("byte b") || content.contains("drop 8b") || content.contains("drop 16b") ) {
                comment.remove();
            }
        }
    }

    /**
     * Remove padding fields from the given class.
     */
    public static void removePaddingFields(ClassOrInterfaceDeclaration node) {
        // remove padding fields
        for (FieldDeclaration field : node.getFields())
        {
            String fieldName = field.getVariables().get(0).getNameAsString();
            if (fieldName.startsWith("b0") || fieldName.startsWith("b1")) {
                node.remove(field);
            }
        }
    }

    /**
     * Build a generator instance of the given class with the given file name.
     */
    public static <T> T buildGenerator(Class<? extends T> generatorClass, String fileName) throws Exception {
        return generatorClass.getDeclaredConstructor(String.class).newInstance(fileName);
    }

    protected static final String INDENT_LEVEL = "    ";

    /**
     * Format a multiline javadoc comment with the given lines and indentation level.
     */
    public static String formatMultilineJavadoc(int indent, String... lines) {
        String indentation = "";
        for (int i = 0; i < indent; i++) {
            indentation += INDENT_LEVEL;
        }

        String out = "\n";
        for (String line : lines) {
            out += indentation + " * " + line + "\n";
        }
        out += indentation + " ";
        return out;
    }

    public static void runJCToolsGenerator(Class<? extends JCToolsGenerator> generatorClass, String[] args) throws Exception {

        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: outputDirectory inputSourceFiles");
        }

        File outputDirectory = new File(args[0]);

        for (int i = 1; i < args.length; i++) {
            File file = new File(args[i]);
            System.out.println("Processing " + file);
            CompilationUnit cu = new JavaParser().parse(file).getResult().get();
            JCToolsGenerator generator = buildGenerator(generatorClass, file.getName());
            generator.visit(cu, null);
            generator.organiseImports(cu);
            generator.cleanupComments(cu);

            String outputFileName = generator.translateQueueName(file.getName().replace(".java", "")) + ".java";

            try (FileWriter writer = new FileWriter(new File(outputDirectory, outputFileName))) {
                // Use custom printer configuration to reduce spacing
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinterVisitor printer = new DefaultPrettyPrinterVisitor(config);
                cu.accept(printer, null);
                String output = printer.toString();

                // Post-process to match Unsafe formatting for padding fields
                output = output.replaceAll("(?m)^\\s*// (\\d+b)\\s*$\\s*byte (b\\d{3}), (b\\d{3}), (b\\d{3}), (b\\d{3}), (b\\d{3}), (b\\d{3}), (b\\d{3}), (b\\d{3});",
                                          "    byte $2,$3,$4,$5,$6,$7,$8,$9;//  $1");

                writer.write(output);
            }

            System.out.println("Saved to " + outputFileName);
        }
    }
}
