package app.revanced.patches.youtube.utils.returnyoutubedislike.shorts

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.textcomponent.TextComponentPatch
import app.revanced.patches.youtube.utils.integrations.Constants.UTILS_PATH
import app.revanced.patches.youtube.utils.returnyoutubedislike.shorts.fingerprints.ShortsTextViewFingerprint
import app.revanced.patches.youtube.utils.settings.SettingsPatch
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Patch(
    dependencies = [
        SettingsPatch::class,
        TextComponentPatch::class
    ]
)
object ReturnYouTubeDislikeShortsPatch : BytecodePatch(
    setOf(ShortsTextViewFingerprint)
) {
    private const val INTEGRATIONS_RYD_CLASS_DESCRIPTOR =
        "$UTILS_PATH/ReturnYouTubeDislikePatch;"

    override fun execute(context: BytecodeContext) {
        ShortsTextViewFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val startIndex = it.scanResult.patternScanResult!!.startIndex

                val isDisLikesBooleanIndex =
                    indexOfFirstInstructionReversedOrThrow(startIndex, Opcode.IGET_BOOLEAN)
                val textViewFieldIndex =
                    indexOfFirstInstructionReversedOrThrow(startIndex, Opcode.IGET_OBJECT)

                // If the field is true, the TextView is for a dislike button.
                val isDisLikesBooleanReference =
                    getInstruction<ReferenceInstruction>(isDisLikesBooleanIndex).reference

                val textViewFieldReference = // Like/Dislike button TextView field
                    getInstruction<ReferenceInstruction>(textViewFieldIndex).reference

                // Check if the hooked TextView object is that of the dislike button.
                // If RYD is disabled, or the TextView object is not that of the dislike button, the execution flow is not interrupted.
                // Otherwise, the TextView object is modified, and the execution flow is interrupted to prevent it from being changed afterward.
                val insertIndex = indexOfFirstInstructionOrThrow(Opcode.CHECK_CAST) + 1

                addInstructionsWithLabels(
                    insertIndex, """
                    # Check, if the TextView is for a dislike button
                    iget-boolean v0, p0, $isDisLikesBooleanReference
                    if-eqz v0, :ryd_disabled
                    
                    # Hook the TextView, if it is for the dislike button
                    iget-object v0, p0, $textViewFieldReference
                    invoke-static {v0}, $INTEGRATIONS_RYD_CLASS_DESCRIPTOR->setShortsDislikes(Landroid/view/View;)Z
                    move-result v0
                    if-eqz v0, :ryd_disabled
                    return-void
                    """, ExternalLabel("ryd_disabled", getInstruction(insertIndex))
                )
            }
        }

        if (SettingsPatch.upward1834) {
            TextComponentPatch.hookSpannableString(
                INTEGRATIONS_RYD_CLASS_DESCRIPTOR,
                "onCharSequenceLoaded"
            )
        }
    }
}
