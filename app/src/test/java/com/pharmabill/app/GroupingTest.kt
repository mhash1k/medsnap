package com.pharmabill.app

import com.pharmabill.app.ai.count.Letterbox
import com.pharmabill.app.ai.count.Nms
import com.pharmabill.app.ai.count.YoloDecoder
import com.pharmabill.app.ai.group.DetectionGrouper
import com.pharmabill.app.ai.group.NameHit
import com.pharmabill.app.ai.group.ScanAssembler
import com.pharmabill.app.ai.match.MatchCandidate
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.DetClass
import com.pharmabill.app.domain.model.Detection
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.SaleUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    private fun tablet(x: Float, y: Float, conf: Float = 0.9f) = Detection(DetClass.TABLET, BBox(x, y, x + 40, y + 40), conf)
    private fun strip(x0: Float, y0: Float, n: Int) = (0 until n).map { tablet(x0 + (it % 5) * 50f, y0 + (it / 5) * 50f) }
    private fun name(x: Float, y: Float, id: Long, score: Double = 0.95) =
        NameHit(BBox(x, y, x + 120, y + 30), listOf(MatchCandidate(id, score)), "id$id")

    @Test fun twoStripsFormTwoGroups() {
        val dets = strip(0f, 0f, 10) + strip(1000f, 0f, 4)
        val groups = DetectionGrouper.cluster(dets)
        assertEquals(2, groups.size)
        assertEquals(setOf(10, 4), groups.map { it.count }.toSet())
    }

    @Test fun boxIsOwnGroup() {
        val dets = strip(0f, 0f, 3) + Detection(DetClass.BOX, BBox(500f, 500f, 900f, 800f), 0.8f)
        val groups = DetectionGrouper.cluster(dets)
        assertEquals(2, groups.size)
        assertEquals(1, groups.count { it.isBox })
    }

    @Test fun namesAttachToContainingOrNearestGroup() {
        val dets = strip(0f, 0f, 12) + strip(1500f, 0f, 4)
        val names = listOf(name(50f, 30f, 1), name(1500f, 160f, 2))
        val items = DetectionGrouper.group(dets, names, 2000, 1000, oneMedicineMode = false)
        assertEquals(2, items.size)
        assertEquals(1L, items.first { it.group?.count == 12 }.name?.topId)
        assertEquals(2L, items.first { it.group?.count == 4 }.name?.topId)
    }

    @Test fun groupWithoutNameAndNameWithoutGroup() {
        val dets = strip(0f, 0f, 5)
        val names = listOf(name(3000f, 3000f, 4))
        val items = DetectionGrouper.group(dets, names, 4000, 4000, oneMedicineMode = false)
        assertEquals(2, items.size)
        assertNull(items.first { it.group != null }.name)
        assertNull(items.first { it.name != null }.group)
    }

    @Test fun twoNamesOnOneGroupKeepHigherAsPrimary() {
        val dets = strip(0f, 0f, 10)
        val names = listOf(name(10f, 10f, 1, 0.8), name(60f, 60f, 5, 0.95))
        val item = DetectionGrouper.group(dets, names, 1000, 1000, false).single()
        assertEquals(5L, item.name?.topId)
        assertEquals(1L, item.alternatives.single().topId)
    }

    @Test fun oneMedicineModeMergesEverything() {
        val dets = strip(0f, 0f, 6) + strip(1500f, 0f, 4)
        val names = listOf(name(0f, 0f, 1, 0.9), name(1500f, 0f, 2, 0.7))
        val item = DetectionGrouper.group(dets, names, 2000, 1000, true).single()
        assertEquals(10, item.group?.count)
        assertEquals(1L, item.name?.topId)
    }

    @Test fun assemblerBuildsLinesAndFlags() {
        val asm = ScanAssembler(FlagEvaluator()) { TestData.infos[it] }
        val dets = strip(0f, 0f, 12) + strip(1500f, 0f, 4) + strip(0f, 1500f, 3)
        val names = listOf(name(50f, 30f, 1), name(1500f, 30f, 7), name(3000f, 3000f, 4))
        val items = DetectionGrouper.group(dets, names, 4000, 4000, false)
        val lines = asm.assemble(items, modelAvailable = true)
        val napa = lines.first { it.medicineId == 1L }
        assertEquals(SaleUnit.STRIP, napa.unit); assertEquals(1, napa.quantity); assertEquals(Flag.GREEN, napa.flag)
        val fexo = lines.first { it.medicineId == 7L }
        assertEquals(SaleUnit.PIECE, fexo.unit); assertEquals(4, fexo.quantity)
        val unknown = lines.first { it.medicineId == null }
        assertEquals(Flag.RED, unknown.flag); assertEquals(3, unknown.quantity)
        val syrup = lines.first { it.medicineId == 4L }
        assertEquals(SaleUnit.UNIT, syrup.unit); assertEquals(Flag.GREEN, syrup.flag)
    }

    @Test fun noModelModeIsYellow() {
        val asm = ScanAssembler(FlagEvaluator()) { TestData.infos[it] }
        val items = DetectionGrouper.group(emptyList(), listOf(name(0f, 0f, 1)), 1000, 1000, false)
        val line = asm.assemble(items, modelAvailable = false).single()
        assertEquals(Flag.YELLOW, line.flag)
        assertEquals(12, line.quantity)
    }

    @Test fun boxLineAlwaysYellow() {
        val asm = ScanAssembler(FlagEvaluator()) { TestData.infos[it] }
        val dets = listOf(Detection(DetClass.BOX, BBox(0f, 0f, 400f, 300f), 0.95f))
        val items = DetectionGrouper.group(dets, listOf(name(50f, 50f, 2)), 1000, 1000, false)
        val line = asm.assemble(items, true).single()
        assertEquals(SaleUnit.BOX, line.unit); assertEquals(Flag.YELLOW, line.flag)
    }

    @Test fun nmsRemovesOverlaps() {
        val a = Detection(DetClass.TABLET, BBox(0f, 0f, 10f, 10f), 0.9f)
        val b = Detection(DetClass.TABLET, BBox(1f, 1f, 11f, 11f), 0.8f)
        val c = Detection(DetClass.TABLET, BBox(50f, 50f, 60f, 60f), 0.7f)
        assertEquals(listOf(a, c), Nms.apply(listOf(b, a, c), 0.5f))
    }

    @Test fun yoloDecodeChannelsFirstNormalised() {
        // 2 anchors, 3 classes: [1, 7, 2]
        val n = 2
        val out = FloatArray(7 * n)
        fun set(a: Int, ch: Int, v: Float) { out[ch * n + a] = v }
        set(0, 0, 0.5f); set(0, 1, 0.5f); set(0, 2, 0.1f); set(0, 3, 0.1f); set(0, 4, 0.9f)
        set(1, 0, 0.2f); set(1, 1, 0.2f); set(1, 2, 0.1f); set(1, 3, 0.1f); set(1, 6, 0.3f)
        val lb = Letterbox.compute(640, 640, 640)
        val dets = YoloDecoder.decode(out, intArrayOf(1, 7, n), 3, 0.4f, lb, 640, 640)
        assertEquals(1, dets.size)
        assertEquals(DetClass.TABLET, dets[0].cls)
        assertEquals(320f, dets[0].box.cx, 0.5f)
    }

    @Test fun letterboxMapsBack() {
        val lb = Letterbox.compute(1280, 640, 640)
        assertEquals(0.5f, lb.scale, 1e-6f)
        assertEquals(160f, lb.padY, 1e-3f)
        val (x, y) = lb.toImage(320f, 320f)
        assertEquals(640f, x, 1e-3f); assertEquals(320f, y, 1e-3f)
        assertTrue(lb.padX == 0f)
    }
}
