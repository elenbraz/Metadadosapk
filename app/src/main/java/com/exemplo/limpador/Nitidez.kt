package com.exemplo.limpador

import android.content.Context
import android.opengl.GLES20
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram

@UnstableApi
class NitidezEffect(private val forca: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        NitidezShader(forca)
}

@UnstableApi
private class NitidezShader(private val forca: Float) : BaseGlShaderProgram(false, 1) {

    private val programa = GlProgram(VERTEX, FRAGMENT)
    private var largura = 1
    private var altura = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        largura = inputWidth
        altura = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        programa.use()
        programa.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        programa.setFloatsUniform("uTexel", floatArrayOf(1f / largura, 1f / altura))
        programa.setFloatUniform("uForca", forca)
        programa.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        programa.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        programa.delete()
    }

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""
        private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uForca;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
  vec4 b = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexel.x, 0.0))
         + texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexel.x, 0.0))
         + texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexel.y))
         + texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexel.y));
  b = b * 0.25;
  vec3 nitido = c.rgb + uForca * (c.rgb - b.rgb);
  gl_FragColor = vec4(clamp(nitido, 0.0, 1.0), c.a);
}
"""
    }
}
