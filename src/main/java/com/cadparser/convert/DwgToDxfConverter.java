package com.cadparser.convert;

import com.cadparser.config.CadProperties;
import com.cadparser.parse.CadParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * DWG is Autodesk proprietary. This converter shells out to an external tool,
 * then the rest of the pipeline parses the resulting ASCII DXF.
 */
@Component
public class DwgToDxfConverter {

    private static final Logger log = LoggerFactory.getLogger(DwgToDxfConverter.class);

    private final CadProperties properties;

    public DwgToDxfConverter(CadProperties properties) {
        this.properties = properties;
    }

    public Path convert(Path dwgFile) {
        String mode = properties.getDwg().getConverter().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "none" -> throw missingConverter("DWG conversion is disabled (cad.dwg.converter=none).");
            case "oda" -> convertWithOda(dwgFile);
            case "dwg2dxf" -> convertWithLibreDwg(dwgFile);
            case "auto" -> convertAuto(dwgFile);
            default -> throw new CadParseException("Unknown cad.dwg.converter: " + mode);
        };
    }

    public String describeAvailability() {
        Optional<Path> oda = resolveOda();
        boolean dwg2dxf = commandExists(properties.getDwg().getDwg2dxfPath());
        return "converter=" + properties.getDwg().getConverter()
                + ", oda=" + oda.map(Path::toString).orElse("not-found")
                + ", dwg2dxf=" + (dwg2dxf ? properties.getDwg().getDwg2dxfPath() : "not-found");
    }

    private Path convertAuto(Path dwgFile) {
        Optional<Path> oda = resolveOda();
        if (oda.isPresent()) {
            return convertWithOda(dwgFile, oda.get());
        }
        if (commandExists(properties.getDwg().getDwg2dxfPath())) {
            return convertWithLibreDwg(dwgFile);
        }
        throw missingConverter(
                "No DWG converter found. Install ODA File Converter or LibreDWG dwg2dxf, "
                        + "or export the drawing as ASCII DXF. " + describeAvailability()
        );
    }

    private Path convertWithOda(Path dwgFile) {
        Path oda = resolveOda().orElseThrow(() -> missingConverter("ODA File Converter not found."));
        return convertWithOda(dwgFile, oda);
    }

    private Path convertWithOda(Path dwgFile, Path odaExe) {
        try {
            Path inputDir = dwgFile.getParent();
            Path outputDir = Files.createTempDirectory("cad-oda-out-");
            List<String> command = List.of(
                    odaExe.toString(),
                    inputDir.toAbsolutePath().toString(),
                    outputDir.toAbsolutePath().toString(),
                    properties.getDwg().getOutputVersion(),
                    "DXF",
                    "0",
                    "1",
                    dwgFile.getFileName().toString()
            );
            run(command);
            Path dxf = findDxf(outputDir);
            if (dxf == null) {
                throw new CadParseException("ODA File Converter produced no DXF in " + outputDir);
            }
            return dxf;
        } catch (IOException e) {
            throw new CadParseException("Failed to convert DWG with ODA", e);
        }
    }

    private Path convertWithLibreDwg(Path dwgFile) {
        try {
            Path outputDir = Files.createTempDirectory("cad-dwg2dxf-");
            Path dxf = outputDir.resolve(replaceExt(dwgFile.getFileName().toString(), ".dxf"));
            List<String> command = List.of(
                    properties.getDwg().getDwg2dxfPath(),
                    "-o",
                    dxf.toAbsolutePath().toString(),
                    dwgFile.toAbsolutePath().toString()
            );
            run(command);
            if (!Files.exists(dxf)) {
                Path found = findDxf(outputDir);
                if (found == null) {
                    throw new CadParseException("dwg2dxf produced no DXF");
                }
                return found;
            }
            return dxf;
        } catch (IOException e) {
            throw new CadParseException("Failed to convert DWG with dwg2dxf", e);
        }
    }

    private void run(List<String> command) {
        log.info("Running DWG converter: {}", command);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(Duration.ofSeconds(properties.getDwg().getTimeoutSeconds()).toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new CadParseException("DWG converter timed out: " + command);
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new CadParseException("DWG converter failed (" + process.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CadParseException("DWG converter interrupted", e);
        } catch (IOException e) {
            throw new CadParseException("Unable to start DWG converter " + command, e);
        }
    }

    private Optional<Path> resolveOda() {
        String configured = properties.getDwg().getOdaConverterPath();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isExecutable(path) || Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of("C:\\Program Files\\ODA"));
        roots.add(Path.of("C:\\Program Files (x86)\\ODA"));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, 4)) {
                Optional<Path> found = walk
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("ODAFileConverter.exe"))
                        .sorted(Comparator.comparing(Path::toString).reversed())
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            } catch (IOException ignored) {
                // keep looking
            }
        }
        return Optional.empty();
    }

    private boolean commandExists(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        Path asPath = Path.of(command);
        if (Files.isRegularFile(asPath)) {
            return true;
        }
        try {
            Process process = new ProcessBuilder(command, "--help").redirectErrorStream(true).start();
            process.waitFor(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Path findDxf(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.dxf")) {
            for (Path path : stream) {
                return path;
            }
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".dxf")).findFirst().orElse(null);
        }
    }

    private String replaceExt(String name, String ext) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ext;
    }

    private CadParseException missingConverter(String message) {
        return new CadParseException(message);
    }
}
