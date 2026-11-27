package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.CostingStructureType
import com.example.data.model.SolarSystemType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseAiPhotoRepo"

/**
 * Runtime AI Site Photo Placement powered by Firebase AI Logic (`gemini-nano-banana-2.1`).
 * Never swallows exceptions or fabricates mock Bitmaps — real errors are surfaced to the UI.
 */
class FirebaseAiPhotoRepository {

    private val imageEditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun placeSolarPanelsOnRooftop(
        beforeBitmap: Bitmap,
        capacityKw: Double,
        panelCount: Int,
        structureType: CostingStructureType,
        systemType: SolarSystemType
    ): Result<Bitmap> {
        val structureInstructions = when (structureType) {
            CostingStructureType.STANDARD_RCC ->
                "a standard low-rise hot-dip galvanized iron (HDGI) RCC mounting structure anchored cleanly with chemical fasteners (~0.9m rear leg height, ~24 degree south-facing tilt)"
            CostingStructureType.ELEVATED_GAZEBO ->
                "an elevated high-rise HDGI Gazebo/Pergola steel structure (~2.75m / 9ft walkable headroom underneath with sturdy vertical galvanized columns, cross-bracing, and overhead purlins)"
            CostingStructureType.TIN_SHED ->
                "a flush-mounted aluminum rail and L-clamp mounting kit bolted neatly onto the existing sloped tin shed roof ribs"
        }

        val prompt = """
            Photorealistically edit this exact customer rooftop photograph to show a newly installed ${capacityKw} kW ${systemType.label} solar power plant by NB Engineering Works & Electricals.
            Place an array of $panelCount sleek blue-black monocrystalline DCR solar PV panels mounted on $structureInstructions.
            Keep the exact original rooftop walls, parapet, surrounding houses, sky, and camera angle intact.
            Add realistic metallic shadows on the roof surface and neat UV DC conduit along the parapet. Do not add text overlays or watermarks.
        """.trimIndent()

        return runImageEdit(beforeBitmap, prompt, "Rooftop Solar Placement")
    }

    suspend fun placeInverterOnWall(
        beforeBitmap: Bitmap,
        capacityKw: Double,
        systemType: SolarSystemType
    ): Result<Bitmap> {
        val batteryNote = if (systemType != SolarSystemType.ON_GRID_DCR) {
            "Also place a compact wall/floor-mounted white LiFePO4 lithium solar battery rack neatly below the inverter."
        } else {
            ""
        }

        val prompt = """
            Photorealistically edit this exact customer wall photograph to show a clean, professional ${capacityKw} kW ${systemType.label} solar electrical installation.
            Mount a sleek modern solar inverter cleanly at eye level on the wall, flanked by neat white/grey ACDB and DCDB protection distribution boxes, a red-yellow rotary DC isolator switch, 25mm grey PVC conduit pipes with right-angle bends, and green earthing wire routing.
            $batteryNote
            Preserve the original wall paint, tiles, doors, lighting, and perspective accurately. Do not add text watermarks.
        """.trimIndent()

        return runImageEdit(beforeBitmap, prompt, "Inverter Wall Placement")
    }

    private suspend fun runImageEdit(
        inputBitmap: Bitmap,
        instruction: String,
        opLabel: String
    ): Result<Bitmap> {
        return try {
            val response = withTimeout(60_000L) {
                imageEditModel.generateContent(
                    content {
                        image(inputBitmap)
                        text(instruction)
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            val outBitmap = imagePart?.image
            if (outBitmap != null) {
                Result.success(outBitmap)
            } else {
                Result.failure(IllegalStateException("$opLabel returned no edited image from Gemini."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$opLabel timed out after 60s", e)
            Result.failure(Exception("$opLabel timed out after 60 seconds. Please retry with a clear photo."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "$opLabel quota exceeded", e)
            Result.failure(Exception("AI image generation quota exceeded. Check Firebase Blaze billing/quota and retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$opLabel failed", e)
            Result.failure(Exception(e.localizedMessage ?: "$opLabel failed."))
        }
    }
}
