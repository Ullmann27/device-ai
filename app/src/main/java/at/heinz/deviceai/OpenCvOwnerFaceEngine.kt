package at.heinz.deviceai

import android.content.Context
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/** Real pinned YuNet and SFace. */
internal class OpenCvOwnerFaceEngine(context: Context) {
    private val detector: FaceDetectorYN
    private val recognizer: FaceRecognizerSF

    init {
        check(OpenCVLoader.initLocal())
        val detectorPath = verifiedAsset(context, OwnerFaceModelContract.DETECTOR_ASSET,
            OwnerFaceModelContract.DETECTOR_BYTES, OwnerFaceModelContract.DETECTOR_SHA256)
        val recognizerPath = verifiedAsset(context, OwnerFaceModelContract.RECOGNIZER_ASSET,
            OwnerFaceModelContract.RECOGNIZER_BYTES, OwnerFaceModelContract.RECOGNIZER_SHA256)
        detector = FaceDetectorYN.create(detectorPath, "", Size(320.0, 240.0), .9f, .3f, 500,
            Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU)
        recognizer = FaceRecognizerSF.create(recognizerPath, "", Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU)
    }

    fun observe(frame: OwnerFaceFrame): OwnerFaceObservation {
        require(frame.valid())
        val raw = Mat(frame.height + frame.height / 2, frame.width, CvType.CV_8UC1)
        val bgr = Mat()
        val upright = Mat()
        val faces = Mat()
        val aligned = Mat()
        val embedding = Mat()
        try {
            raw.put(0, 0, frame.yuv420)
            Imgproc.cvtColor(raw, bgr, Imgproc.COLOR_YUV2BGR_I420)
            when (frame.rotationDegrees) {
                90 -> Core.rotate(bgr, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(bgr, upright, Core.ROTATE_180)
                270 -> Core.rotate(bgr, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> bgr.copyTo(upright)
            }
            detector.setInputSize(Size(upright.cols().toDouble(), upright.rows().toDouble()))
            detector.detect(upright, faces)
            if (faces.rows() != 1) return OwnerFaceObservation(faces.rows(), frame.observedAtElapsedMillis)
            if (faces.cols() != 15 || faces.type() != CvType.CV_32FC1) {
                return OwnerFaceObservation(-1, frame.observedAtElapsedMillis)
            }
            val values = FloatArray(15)
            faces.get(0, 0, values)
            if (values.any { !it.isFinite() }) return OwnerFaceObservation(-1, frame.observedAtElapsedMillis)
            val quality = quality(upright, values)
            if (!quality.acceptable()) return OwnerFaceObservation(1, frame.observedAtElapsedMillis, quality)
            recognizer.alignCrop(upright, faces, aligned)
            recognizer.feature(aligned, embedding)
            if (embedding.total() != OwnerFaceModelContract.FEATURE_SIZE.toLong() ||
                embedding.type() != CvType.CV_32FC1) return OwnerFaceObservation(-1, frame.observedAtElapsedMillis)
            val feature = FloatArray(OwnerFaceModelContract.FEATURE_SIZE)
            embedding.get(0, 0, feature)
            return OwnerFaceObservation(1, frame.observedAtElapsedMillis, quality, feature)
        } finally {
            embedding.release()
            aligned.release()
            faces.release()
            upright.release()
            bgr.release()
            raw.release()
        }
    }

    private fun quality(image: Mat, face: FloatArray): OwnerFaceQuality {
        val dx = face[6] - face[4]
        val dy = face[7] - face[5]
        val eyeDistance = sqrt(dx * dx + dy * dy)
        val eyeSlope = if (abs(dx) < 1f) Float.POSITIVE_INFINITY else dy / dx
        val noseOffset = if (eyeDistance < 1f) Float.POSITIVE_INFINITY else
            (face[8] - (face[4] + face[6]) / 2f) / eyeDistance
        var brightness = Double.NaN
        var variance = Double.NaN
        if (face[0] >= 0f && face[1] >= 0f && face[2] >= 64f && face[3] >= 64f &&
            face[0] + face[2] <= image.cols() && face[1] + face[3] <= image.rows()) {
            val crop = image.submat(Rect(face[0].toInt(), face[1].toInt(), face[2].toInt(), face[3].toInt()))
            val gray = Mat()
            val laplacian = Mat()
            val mean = MatOfDouble()
            val deviation = MatOfDouble()
            try {
                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
                brightness = Core.mean(gray).`val`[0]
                Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
                Core.meanStdDev(laplacian, mean, deviation)
                val standardDeviation = deviation.toArray().single()
                variance = standardDeviation * standardDeviation
            } finally {
                deviation.release()
                mean.release()
                laplacian.release()
                gray.release()
                crop.release()
            }
        }
        return OwnerFaceQuality(image.cols(), image.rows(), face[0], face[1], face[2], face[3], face[14],
            eyeDistance, eyeSlope, noseOffset, brightness, variance)
    }

    private fun verifiedAsset(context: Context, asset: String, expectedSize: Long, expectedSha: String): String {
        val directory = File(context.codeCacheDir, "owner-face-models")
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, asset.substringAfterLast('/'))
        if (file.length() == expectedSize && sha256(file) == expectedSha) return file.absolutePath
        val temporary = File(directory, file.name + ".pending")
        try {
            context.assets.open(asset).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(16_384)
                    var count = 0L
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes < 0) break
                        count += bytes
                        check(count <= expectedSize)
                        output.write(buffer, 0, bytes)
                    }
                    check(count == expectedSize)
                    output.fd.sync()
                }
            }
            check(sha256(temporary) == expectedSha)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return file.absolutePath
        } finally { temporary.delete() }
    }

    private fun sha256(file: File): String {
        if (!file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16_384)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
