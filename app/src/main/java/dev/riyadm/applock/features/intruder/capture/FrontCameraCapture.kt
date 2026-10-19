package dev.riyadm.applock.features.intruder.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Takes one still photo with the front camera using Camera2, without any preview on screen
 * and without a shutter sound (Camera2 never plays one by itself). A tiny YUV stream that is
 * discarded frame by frame lets auto exposure settle before the still is captured.
 *
 * [capture] blocks the calling thread (never the main thread) for at most [TIMEOUT_MS] and
 * returns null whenever the camera is missing, busy, blocked or denied.
 */
internal class FrontCameraCapture(context: Context) {
    private val context = context.applicationContext

    fun capture(): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService<CameraManager>() ?: return null
        val cameraId = findFrontCamera(manager) ?: return null
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG)) ?: return null
        val previewSize = choosePreviewSize(map.getOutputSizes(ImageFormat.YUV_420_888)) ?: return null
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        val continuousAf = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true

        val thread = HandlerThread("IntruderCamera").apply { start() }
        val handler = Handler(thread.looper)
        val session = try {
            CaptureSession(manager, cameraId, jpegSize, previewSize, continuousAf, thread, handler)
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare capture", e)
            thread.quitSafely()
            return null
        }
        val raw = try {
            handler.post(session::start)
            session.result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Capture did not finish: ${e.javaClass.simpleName}")
            null
        } finally {
            // close() quits the thread once the camera is released
            handler.post(session::close)
        }
        raw ?: return null

        val rotation = (sensorOrientation + displayRotationDegrees()) % 360
        return normalize(raw, rotation)
    }

    private fun findFrontCamera(manager: CameraManager): String? = try {
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraMetadata.LENS_FACING_FRONT
        }
    } catch (e: Exception) {
        Log.w(TAG, "Camera list unavailable", e)
        null
    }

    /** The smallest JPEG size whose long edge still reaches ~720p, else the largest one. */
    private fun chooseJpegSize(sizes: Array<Size>?): Size? {
        if (sizes.isNullOrEmpty()) return null
        return sizes.filter { max(it.width, it.height) >= MAX_EDGE_PX }
            .minByOrNull { it.width.toLong() * it.height }
            ?: sizes.maxBy { it.width.toLong() * it.height }
    }

    private fun choosePreviewSize(sizes: Array<Size>?): Size? {
        if (sizes.isNullOrEmpty()) return null
        return sizes.filter { it.width * it.height >= 320 * 240 }
            .minByOrNull { it.width * it.height }
            ?: sizes.minBy { it.width * it.height }
    }

    private fun displayRotationDegrees(): Int {
        val rotation = try {
            context.getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        } catch (_: Exception) {
            null
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    /** Scales to [MAX_EDGE_PX], rotates upright and re-encodes, which also drops all metadata. */
    private fun normalize(jpeg: ByteArray, rotation: Int): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalStateException("Undecodable frame")
        val scale = min(1f, MAX_EDGE_PX.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        if (isBlank(upright)) {
            // The quick-settings camera block delivers black frames; don't keep those
            upright.recycle()
            throw IllegalStateException("Blank frame")
        }
        ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            upright.recycle()
            out.toByteArray()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not process photo", e)
        null
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val probe = bitmap.scale(16, 16)
        val pixels = IntArray(16 * 16)
        probe.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (probe !== bitmap) probe.recycle()
        return pixels.all { pixel ->
            maxOf(pixel shr 16 and 0xff, pixel shr 8 and 0xff, pixel and 0xff) < BLANK_THRESHOLD
        }
    }

    /** All camera state is confined to the handler thread. */
    private class CaptureSession(
        private val manager: CameraManager,
        private val cameraId: String,
        jpegSize: Size,
        previewSize: Size,
        private val continuousAf: Boolean,
        private val thread: HandlerThread,
        private val handler: Handler
    ) {
        val result = CompletableFuture<ByteArray?>()

        private val jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1)
        private val previewReader =
            ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 3)
        private var device: CameraDevice? = null
        private var session: CameraCaptureSession? = null
        private var frames = 0
        private var stillRequested = false
        private var openPending = false
        private var closed = false

        @SuppressLint("MissingPermission")
        fun start() {
            previewReader.setOnImageAvailableListener({ reader ->
                try {
                    reader.acquireLatestImage()?.close()
                } catch (_: Exception) {
                }
            }, handler)
            jpegReader.setOnImageAvailableListener({ reader ->
                try {
                    reader.acquireNextImage()?.use { image ->
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                        result.complete(bytes)
                    }
                } catch (e: Exception) {
                    fail("JPEG read failed", e)
                }
            }, handler)
            try {
                openPending = true
                manager.openCamera(cameraId, stateCallback, handler)
            } catch (e: Exception) {
                // CameraAccessException (busy / disabled by policy), SecurityException (denied)
                openPending = false
                fail("openCamera failed", e)
            }
        }

        private val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                openPending = false
                if (closed) {
                    // Opened after we gave up: release it right away
                    camera.close()
                    thread.quitSafely()
                    return
                }
                device = camera
                createSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                openPending = false
                device = camera
                fail("Camera disconnected", null)
                if (closed) release() else close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                openPending = false
                device = camera
                fail("Camera error $error", null)
                if (closed) release() else close()
            }
        }

        private val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(captureSession: CameraCaptureSession) {
                if (closed) {
                    captureSession.close()
                    return
                }
                session = captureSession
                startMetering(captureSession)
            }

            override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                fail("Session configuration failed", null)
            }
        }

        private val meteringCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                frames++
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                val settled = aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_LOCKED
                if ((settled && frames >= MIN_METERING_FRAMES) || frames >= MAX_METERING_FRAMES) {
                    captureStill()
                }
            }
        }

        private fun createSession(camera: CameraDevice) {
            val surfaces = listOf(previewReader.surface, jpegReader.surface)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val executor = Executor { handler.post(it) }
                    camera.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            surfaces.map { OutputConfiguration(it) },
                            executor,
                            sessionCallback
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(surfaces, sessionCallback, handler)
                }
            } catch (e: Exception) {
                fail("createCaptureSession failed", e)
            }
        }

        private fun startMetering(captureSession: CameraCaptureSession) {
            try {
                val request = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewReader.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    if (continuousAf) {
                        set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                }.build()
                captureSession.setRepeatingRequest(request, meteringCallback, handler)
                // Some HALs report no results while metering; don't wait for them forever.
                handler.postDelayed(::captureStill, METERING_FALLBACK_MS)
            } catch (e: Exception) {
                fail("Metering failed", e)
            }
        }

        private fun captureStill() {
            if (stillRequested || closed) return
            val captureSession = session ?: return
            stillRequested = true
            try {
                val request = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(jpegReader.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    if (continuousAf) {
                        set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    set(CaptureRequest.JPEG_QUALITY, 92.toByte())
                }.build()
                captureSession.capture(request, null, handler)
            } catch (e: Exception) {
                fail("Still capture failed", e)
            }
        }

        private fun fail(message: String, error: Exception?) {
            if (error is CameraAccessException || error is SecurityException || error == null) {
                Log.w(TAG, "$message${error?.let { ": ${it.message}" }.orEmpty()}")
            } else {
                Log.w(TAG, message, error)
            }
            result.complete(null)
        }

        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacksAndMessages(null)
            try {
                session?.close()
            } catch (_: Exception) {
            }
            session = null
            if (openPending) {
                // The open callback still has to arrive on this thread so the device can be
                // released; quit anyway if it never does.
                handler.postDelayed(thread::quitSafely, OPEN_GRACE_MS)
            } else {
                release()
            }
            previewReader.close()
            jpegReader.close()
            result.complete(null)
        }

        private fun release() {
            try {
                device?.close()
            } catch (_: Exception) {
            }
            device = null
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "IntruderCamera"
        const val TIMEOUT_MS = 6_000L
        const val METERING_FALLBACK_MS = 1_500L
        const val OPEN_GRACE_MS = 10_000L
        const val MIN_METERING_FRAMES = 4
        const val MAX_METERING_FRAMES = 45
        const val MAX_EDGE_PX = 1280
        const val JPEG_QUALITY = 82
        const val BLANK_THRESHOLD = 6
    }
}
