package com.quimodotcom.lqlauncher.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.quimodotcom.lqlauncher.helpers.DebugLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class VideoWallpaperRenderer(private val context: Context) {
    private val TAG = "VideoWallpaperRenderer"

    // EGL State
    private var egl: EGL10? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // GL State
    private var videoTextureId = 0
    private var uiTextureId = 0
    private var bgTextureId = 0
    private var programId = 0
    private var uiProgramId = 0

    // Video State
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var updateSurface = false
    private var videoFrameAvailable = false
    private var isSurfaceValid = false
    private var lastDrawLogTime = 0L

    // Geometry
    private val triangleVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0f, 0f,
        1.0f, -1.0f, 0f, 1f, 0f,
        -1.0f, 1.0f, 0f, 0f, 1f,
        1.0f, 1.0f, 0f, 1f, 1f
    )
    private var triangleVertices: FloatBuffer? = null
    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private var videoRatio = 1.777f // Default to 16:9 to avoid initial distortion

    // UI State
    private var uiBitmap: Bitmap? = null
    private var uiDirty = false

    // Background State
    private var bgBitmap: Bitmap? = null
    private var bgDirty = false

    init {
        triangleVertices = ByteBuffer.allocateDirect(triangleVerticesData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices?.put(triangleVerticesData)?.position(0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    fun onSurfaceCreated(holder: android.view.SurfaceHolder) {
        initEGL(holder)
        initGL()
    }

    fun onSurfaceDestroyed() {
        releaseMediaPlayer()
        destroyGL()
        destroyEGL()
    }

    fun stop() {
        synchronized(this) {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    mediaPlayer?.seekTo(0)
                }
            } catch (e: Exception) {
                DebugLogger.log(TAG, "Stop error: ${e.message}")
            }
        }
    }

    fun reset() {
        synchronized(this) {
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer!!.isPlaying) {
                        mediaPlayer!!.stop()
                    }
                    mediaPlayer!!.reset()
                    mediaPlayer!!.release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                DebugLogger.log(TAG, "Reset error: ${e.message}")
            }
            updateSurface = false
        }
    }

    fun setVideoSource(file: java.io.File) {
        synchronized(this) {
            try {
                DebugLogger.log(TAG, "Setting Source: ${file.name} (${file.length()} bytes)")

                if (!file.exists() || !file.canRead()) {
                    DebugLogger.log(TAG, "File error: Not readable/exists")
                    return
                }

                if (file.length() <= 0) {
                    DebugLogger.log(TAG, "File error: Empty file")
                    return
                }

                if (!isSurfaceValid) {
                    DebugLogger.log(TAG, "Skipping: GL context invalid")
                    return
                }

                // Rigorous Stop Cycle
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer!!.isPlaying) {
                            mediaPlayer!!.stop()
                        }
                    } catch (e: Exception) {
                         // Ignore stop errors on dead players
                    }
                    try {
                        mediaPlayer!!.reset()
                        mediaPlayer!!.release()
                    } catch (e: Exception) {
                        DebugLogger.log(TAG, "Release error: ${e.message}")
                    }
                    mediaPlayer = null
                }

                // Total Nuclear Reset of SurfaceTexture
                // This ensures any stuck state from the previous player is wiped.
                try {
                    videoSurface?.release()
                    videoSurface = null

                    surfaceTexture?.release()
                    surfaceTexture = null

                    updateSurface = false
                    videoFrameAvailable = false

                    surfaceTexture = SurfaceTexture(videoTextureId)
                    surfaceTexture!!.setOnFrameAvailableListener {
                        synchronized(this) {
                            updateSurface = true
                            videoFrameAvailable = true
                            // DebugLogger.log(TAG, "Frame Available") // Very spammy
                        }
                    }
                    videoSurface = Surface(surfaceTexture)
                    DebugLogger.log(TAG, "SurfaceTexture Recreated")
                } catch (e: Exception) {
                    DebugLogger.log(TAG, "ST Reset Error: ${e.message}")
                    return
                }

                mediaPlayer = MediaPlayer()

                mediaPlayer?.apply {
                    val fis = java.io.FileInputStream(file)
                    setDataSource(fis.fd)
                    fis.close()

                    setSurface(videoSurface)
                    setOnPreparedListener { mp ->
                        try {
                            mp.isLooping = true
                            val w = mp.videoWidth
                            val h = mp.videoHeight

                            // Set default buffer size for SurfaceTexture
                            surfaceTexture?.setDefaultBufferSize(w, h)

                            videoRatio = if (w > 0 && h > 0) {
                                 w.toFloat() / h
                            } else {
                                 1.777f
                            }
                            mp.start()
                            DebugLogger.log(TAG, "Started: ${w}x${h}")
                        } catch (e: Exception) {
                            DebugLogger.log(TAG, "Start error: ${e.message}")
                        }
                    }
                    setOnCompletionListener { mp ->
                        mp.start()
                    }
                    setOnErrorListener { _, what, extra ->
                        DebugLogger.log(TAG, "MP Error: w=$what, e=$extra")
                        true // Handled
                    }
                    setOnInfoListener { _, what, extra ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            DebugLogger.log(TAG, "Rendering Started")
                        }
                        false
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                DebugLogger.log(TAG, "SetSource Error: ${e.message}")
                Log.e(TAG, "Error setting video source", e)
            }
        }
    }

    fun updateUI(bitmap: Bitmap) {
        uiBitmap = bitmap
        uiDirty = true
    }

    fun setBackground(bitmap: Bitmap?) {
        bgBitmap = bitmap
        bgDirty = true
    }

    fun draw() {
        if (egl == null) return

        // Update Texture
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                updateSurface = false
            }
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Draw Background or Video
        // Only draw video if it is playing AND we have received a valid frame for it
        val isPlaying = mediaPlayer?.isPlaying == true
        if (isPlaying && videoFrameAvailable) {
            // Draw Video
            // Calculate MVP Matrix for Fit Center (Black Bars)
            val screenRatio = if (height > 0) width.toFloat() / height else 1f
            Matrix.setIdentityM(mvpMatrix, 0)

            // Center Crop (Fill Screen)
            if (videoRatio > screenRatio) {
                // Video is wider than screen: scale X to match height, crop width
                Matrix.scaleM(mvpMatrix, 0, videoRatio / screenRatio, 1f, 1f)
            } else {
                // Video is taller than screen: scale Y to match width, crop height
                Matrix.scaleM(mvpMatrix, 0, 1f, screenRatio / videoRatio, 1f)
            }

            useVideoProgram()

            val uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

            val uSTMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastDrawLogTime > 2000) {
                lastDrawLogTime = now
                if (mediaPlayer != null) {
                    DebugLogger.log(TAG, "Draw skip: Playing=$isPlaying, Frame=$videoFrameAvailable")
                }
            }

            // Draw Static Background (Center Crop/Fill)
            if (bgBitmap != null && !bgBitmap!!.isRecycled) {
                if (bgDirty) {
                    loadTexture(bgTextureId, bgBitmap!!)
                    bgDirty = false
                }

                useUiProgram() // Reuse UI program (Standard 2D)

                // Calculate Scale for Center Crop (Fill Screen) for Background
                Matrix.setIdentityM(mvpMatrix, 0)
                // Assuming bitmap mapped to 0..1 UV and -1..1 quad
                // Actually vertex shader expects uMVPMatrix for UI too? Yes.
                // But UI program vertex shader is same as video? No, check createProgram.
                // Assuming standard quad.

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)

                // Pass Identity Matrix (Fill Screen)
                // Or calculate aspect ratio if needed. But usually static art is pre-scaled or we want fill.
                // If bgBitmap is the raw art, we should scale it.
                // Let's just use Identity for now (Fill Quad) assuming pre-scaled or handled elsewhere?
                // LiquidGlassWallpaperService passes scaled bitmap? No, it might pass raw.
                // Let's implement Center Crop for BG.
                val bgW = bgBitmap!!.width
                val bgH = bgBitmap!!.height
                val bgRatio = bgW.toFloat() / bgH
                val screenRatio = if (height > 0) width.toFloat() / height else 1f

                // Center Crop
                if (bgRatio > screenRatio) {
                    // Bitmap wider: scale X to overflow
                    Matrix.scaleM(mvpMatrix, 0, bgRatio / screenRatio, 1f, 1f)
                } else {
                    // Bitmap taller: scale Y to overflow
                    Matrix.scaleM(mvpMatrix, 0, 1f, screenRatio / bgRatio, 1f)
                }

                val uMVPMatrixHandle = GLES20.glGetUniformLocation(uiProgramId, "uMVPMatrix")
                // Check if UI program has uMVPMatrix.
                // Original Vertex Shader has it. Original UI Program uses Vertex Shader.
                // But my previous edit updated VERTEX_SHADER to use uSTMatrix too.
                // Static Texture doesn't have transform matrix usually.
                // I need to handle that.

                // Pass Flip Matrix to uSTMatrix for static textures (Bitmaps are upside down in GL)
                val uSTMatrixHandle = GLES20.glGetUniformLocation(uiProgramId, "uSTMatrix")
                val flipMatrix = FloatArray(16)
                Matrix.setIdentityM(flipMatrix, 0)
                Matrix.translateM(flipMatrix, 0, 0f, 1f, 0f)
                Matrix.scaleM(flipMatrix, 0, 1f, -1f, 1f)

                GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
                GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, flipMatrix, 0)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } else {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
        }

        // Draw UI Overlay
        if (uiBitmap != null && !uiBitmap!!.isRecycled) {
            if (uiDirty) {
                loadUiTexture()
                uiDirty = false
            }

            useUiProgram()

            // Enable Blending for transparency
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA) // Premultiplied alpha usually? Or SRC_ALPHA

            val uMVPMatrixHandle = GLES20.glGetUniformLocation(uiProgramId, "uMVPMatrix")
            val uSTMatrixHandle = GLES20.glGetUniformLocation(uiProgramId, "uSTMatrix")

            // UI fills screen (Identity)
            Matrix.setIdentityM(mvpMatrix, 0)

            // Flip Matrix for UI texture (Bitmaps are upside down in GL)
            val flipMatrix = FloatArray(16)
            Matrix.setIdentityM(flipMatrix, 0)
            Matrix.translateM(flipMatrix, 0, 0f, 1f, 0f)
            Matrix.scaleM(flipMatrix, 0, 1f, -1f, 1f)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, flipMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uiTextureId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        egl?.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private var width = 0
    private var height = 0

    fun onSurfaceChanged(w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    private fun initEGL(holder: android.view.SurfaceHolder) {
        egl = EGLContext.getEGL() as EGL10
        eglDisplay = egl!!.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

        val version = IntArray(2)
        egl!!.eglInitialize(eglDisplay, version)

        val configSpec = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL10.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        egl!!.eglChooseConfig(eglDisplay, configSpec, configs, 1, numConfig)
        val config = configs[0]

        val attrib_list = intArrayOf(0x3098, 2, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION
        eglContext = egl!!.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, attrib_list)
        eglSurface = egl!!.eglCreateWindowSurface(eglDisplay, config, holder, null)

        egl!!.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun destroyEGL() {
        egl?.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
        egl?.eglDestroySurface(eglDisplay, eglSurface)
        egl?.eglDestroyContext(eglDisplay, eglContext)
        egl?.eglTerminate(eglDisplay)
    }

    private fun initGL() {
        // Video Texture (OES)
        val textures = IntArray(3)
        GLES20.glGenTextures(3, textures, 0)
        videoTextureId = textures[0]
        uiTextureId = textures[1]
        bgTextureId = textures[2]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())

        surfaceTexture = SurfaceTexture(videoTextureId)
        surfaceTexture!!.setOnFrameAvailableListener {
            synchronized(this) {
                updateSurface = true
                videoFrameAvailable = true
            }
        }
        videoSurface = Surface(surfaceTexture)
        isSurfaceValid = true

        // If media player was already created, update its surface
        mediaPlayer?.setSurface(videoSurface)

        // Shaders
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        uiProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_UI)
    }

    private fun destroyGL() {
        synchronized(this) {
            isSurfaceValid = false
            GLES20.glDeleteTextures(3, intArrayOf(videoTextureId, uiTextureId, bgTextureId), 0)
            GLES20.glDeleteProgram(programId)
            GLES20.glDeleteProgram(uiProgramId)
            videoSurface?.release()
            videoSurface = null
            surfaceTexture?.release()
            surfaceTexture = null
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadUiTexture() {
        val bmp = uiBitmap ?: return
        loadTexture(uiTextureId, bmp)
    }

    private fun loadTexture(textureId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    private fun useVideoProgram() {
        GLES20.glUseProgram(programId)

        // Bind attributes/uniforms
        val aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        val aTextureCoord = GLES20.glGetAttribLocation(programId, "aTextureCoord")

        triangleVertices?.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
        GLES20.glEnableVertexAttribArray(aPosition)

        triangleVertices?.position(3)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
        GLES20.glEnableVertexAttribArray(aTextureCoord)
    }

    private fun useUiProgram() {
        GLES20.glUseProgram(uiProgramId)

        // Similar to video but standard 2D sampler
        val aPosition = GLES20.glGetAttribLocation(uiProgramId, "aPosition")
        val aTextureCoord = GLES20.glGetAttribLocation(uiProgramId, "aTextureCoord")

        triangleVertices?.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
        GLES20.glEnableVertexAttribArray(aPosition)

        triangleVertices?.position(3)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
        GLES20.glEnableVertexAttribArray(aTextureCoord)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    // Constants
    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private const val FRAGMENT_SHADER_UI = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }

    // Internal OES Constant (since imports might vary)
    object GLES11Ext {
        const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
    }
}
