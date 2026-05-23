// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.opencv.core.*;
// import org.opencv.imgcodecs.Imgcodecs;
// import org.opencv.imgproc.Imgproc;

// import java.io.File;
// import java.nio.file.*;
// import java.util.*;

// public class ImageProcessor {
//     static {
//         System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
//     }

//     private static final ObjectMapper mapper = new ObjectMapper();

//     public interface OutputPathProvider {
//         String getOutputPath(String imagePath, String transformPath);
//     }

//     public static List<JsonNode> loadTransformations(String jsonPath) throws Exception {
//         JsonNode root = mapper.readTree(new File(jsonPath));
//         List<JsonNode> list = new ArrayList<>();

//         JsonNode arr = root.get("transformations");
//         if (arr != null && arr.isArray()) {
//             for (JsonNode node : arr) {
//                 list.add(node);
//             }
//         }

//         return list;
//     }

//     public static Mat applyTransformation(Mat image, JsonNode transform) {
//         String type = transform.get("type").asText();

//         switch (type) {
//             case "grayscale":
//                 return grayscale(image);

//             case "flip_horizontal":
//                 return flip(image, 1);

//             case "flip_vertical":
//                 return flip(image, 0);

//             case "scale":
//                 return scale(image, transform.get("factor").asDouble());

//             case "blur":
//                 return blur(image, transform.get("radius").asDouble());

//             case "rotate":
//                 return rotate(image, transform.get("angle").asDouble());

//             default:
//                 throw new IllegalArgumentException("Unknown transformation type: " + type);
//         }
//     }

//     public static Mat applyAllTransformations(Mat image, List<JsonNode> transformations) {
//         Mat result = image.clone();

//         for (JsonNode transform : transformations) {
//             Mat next = applyTransformation(result, transform);
//             result.release();
//             result = next;
//         }

//         return result;
//     }

//     public static void processImages(
//             String imageDir,
//             String transformationDir,
//             String outputDir,
//             OutputPathProvider getOutputPath
//     ) throws Exception {
//         Files.createDirectories(Paths.get(outputDir));

//         List<Path> imageFiles = new ArrayList<>();
//         try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(imageDir))) {
//             for (Path p : stream) {
//                 String s = p.toString().toLowerCase();
//                 if (s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg")) {
//                     imageFiles.add(p);
//                 }
//             }
//         }

//         List<Path> transformFiles = new ArrayList<>();
//         try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(transformationDir), "*.json")) {
//             for (Path p : stream) {
//                 transformFiles.add(p);
//             }
//         }

//         for (Path transformFile : transformFiles) {
//             List<JsonNode> transformations = loadTransformations(transformFile.toString());

//             for (Path imageFile : imageFiles) {
//                 Mat image = Imgcodecs.imread(imageFile.toString());

//                 if (image.empty()) {
//                     continue;
//                 }

//                 Mat result = applyAllTransformations(image, transformations);

//                 String outputPath = getOutputPath.getOutputPath(
//                         imageFile.toString(),
//                         transformFile.toString()
//                 );

//                 File out = new File(outputPath);
//                 out.getParentFile().mkdirs();

//                 Imgcodecs.imwrite(outputPath, result);

//                 image.release();
//                 result.release();
//             }
//         }
//     }

//     private static Mat grayscale(Mat image) {
//         Mat gray = new Mat();
//         Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

//         Mat rgb = new Mat();
//         Imgproc.cvtColor(gray, rgb, Imgproc.COLOR_GRAY2BGR);

//         gray.release();
//         return rgb;
//     }

//     private static Mat flip(Mat image, int flipCode) {
//         Mat result = new Mat();
//         Core.flip(image, result, flipCode);
//         return result;
//     }

//     private static Mat scale(Mat image, double factor) {
//         Mat result = new Mat();

//         Size newSize = new Size(
//                 Math.max(1, image.cols() * factor),
//                 Math.max(1, image.rows() * factor)
//         );

//         Imgproc.resize(image, result, newSize, 0, 0, Imgproc.INTER_LANCZOS4);
//         return result;
//     }

//     private static Mat blur(Mat image, double radius) {
//         Mat result = new Mat();

//         int ksize = Math.max(1, (int) Math.round(radius * 2 + 1));

//         if (ksize % 2 == 0) {
//             ksize++;
//         }

//         Imgproc.GaussianBlur(
//                 image,
//                 result,
//                 new Size(ksize, ksize),
//                 radius
//         );

//         return result;
//     }

//     private static Mat rotate(Mat image, double angle) {
//         int w = image.cols();
//         int h = image.rows();

//         Point center = new Point(w / 2.0, h / 2.0);
//         Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);

//         double radians = Math.toRadians(angle);
//         double sin = Math.abs(Math.sin(radians));
//         double cos = Math.abs(Math.cos(radians));

//         int newW = (int) Math.floor(w * cos + h * sin);
//         int newH = (int) Math.floor(h * cos + w * sin);

//         double[] m0 = rotationMatrix.get(0, 2);
//         double[] m1 = rotationMatrix.get(1, 2);

//         rotationMatrix.put(0, 2, m0[0] + newW / 2.0 - center.x);
//         rotationMatrix.put(1, 2, m1[0] + newH / 2.0 - center.y);

//         Mat result = new Mat();

//         Imgproc.warpAffine(
//                 image,
//                 result,
//                 rotationMatrix,
//                 new Size(newW, newH)
//         );

//         rotationMatrix.release();
//         return result;
//     }
// }