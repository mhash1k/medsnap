package com.pharmabill.app.ai.group

import com.pharmabill.app.ai.match.MatchCandidate
import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.DetClass
import com.pharmabill.app.domain.model.Detection
import kotlin.math.hypot
import kotlin.math.max

/** A recognised text block with its best medicine candidates. */
data class NameHit(val box: BBox, val candidates: List<MatchCandidate>, val text: String) {
    val topScore: Double get() = candidates.firstOrNull()?.score ?: 0.0
    val topId: Long? get() = candidates.firstOrNull()?.id
}

data class DetGroup(val detections: List<Detection>, val isBox: Boolean) {
    val bounds: BBox = detections.map { it.box }.reduce { a, b -> a.union(b) }
    val count: Int get() = if (isBox) 0 else detections.size
    val meanConfidence: Float get() = if (detections.isEmpty()) 0f else detections.map { it.confidence }.average().toFloat()
}

data class GroupedItem(val group: DetGroup?, val name: NameHit?, val alternatives: List<NameHit> = emptyList())

object DetectionGrouper {

    /** DBSCAN with minPts = 1 over tablet/capsule centres; each box detection is its own group. */
    fun cluster(detections: List<Detection>): List<DetGroup> {
        val pieces = detections.filter { it.cls.isPiece }
        val boxes = detections.filter { it.cls == DetClass.BOX }
        val groups = mutableListOf<DetGroup>()
        if (pieces.isNotEmpty()) {
            val eps = 1.5f * medianWidth(pieces)
            val visited = BooleanArray(pieces.size)
            for (i in pieces.indices) {
                if (visited[i]) continue
                val members = mutableListOf<Int>()
                val queue = ArrayDeque<Int>().apply { add(i) }
                visited[i] = true
                while (queue.isNotEmpty()) {
                    val p = queue.removeFirst()
                    members += p
                    for (j in pieces.indices) {
                        if (visited[j]) continue
                        val d = hypot(pieces[p].box.cx - pieces[j].box.cx, pieces[p].box.cy - pieces[j].box.cy)
                        if (d <= eps) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
                groups += DetGroup(members.sorted().map { pieces[it] }, isBox = false)
            }
        }
        boxes.forEach { groups += DetGroup(listOf(it), isBox = true) }
        return groups
    }

    fun medianWidth(dets: List<Detection>): Float {
        if (dets.isEmpty()) return 0f
        val w = dets.map { max(it.box.width, it.box.height * 0.5f) }.sorted()
        return if (w.size % 2 == 1) w[w.size / 2] else (w[w.size / 2 - 1] + w[w.size / 2]) / 2f
    }

    fun group(
        detections: List<Detection>,
        names: List<NameHit>,
        imageWidth: Int,
        imageHeight: Int,
        oneMedicineMode: Boolean,
    ): List<GroupedItem> {
        val usableNames = names.filter { it.candidates.isNotEmpty() }
        if (oneMedicineMode) return oneMedicine(detections, usableNames)

        val groups = cluster(detections)
        val pieces = detections.filter { it.cls.isPiece }
        val mw = medianWidth(pieces).takeIf { it > 0f } ?: (imageWidth / 20f)
        val limit = max(3f * mw, 0.15f * hypot(imageWidth.toFloat(), imageHeight.toFloat()))

        val attached = HashMap<Int, MutableList<NameHit>>()
        val loose = mutableListOf<NameHit>()
        for (n in usableNames) {
            val containing = groups.indices.filter { groups[it].bounds.expand(mw * 0.5f).contains(n.box.cx, n.box.cy) }
            val idx = if (containing.isNotEmpty()) {
                containing.minBy { groups[it].bounds.area }
            } else {
                groups.indices.minByOrNull { groups[it].bounds.distanceTo(n.box.cx, n.box.cy) }
                    ?.takeIf { groups[it].bounds.distanceTo(n.box.cx, n.box.cy) <= limit }
            }
            if (idx == null) loose += n else attached.getOrPut(idx) { mutableListOf() } += n
        }

        val out = mutableListOf<GroupedItem>()
        groups.forEachIndexed { i, g ->
            val hits = dedupe(attached[i].orEmpty())
            out += GroupedItem(g, hits.firstOrNull(), hits.drop(1))
        }
        // Names with no group: merge repeats of the same medicine (brand printed twice on a strip).
        val alreadyNamed = out.mapNotNull { it.name?.topId }.toSet()
        dedupe(loose).filter { it.topId !in alreadyNamed }.forEach { out += GroupedItem(null, it) }
        return out
    }

    private fun oneMedicine(detections: List<Detection>, names: List<NameHit>): List<GroupedItem> {
        val pieces = detections.filter { it.cls.isPiece }
        val boxes = detections.filter { it.cls == DetClass.BOX }
        val group = when {
            boxes.isNotEmpty() -> DetGroup(boxes.take(1), isBox = true)
            pieces.isNotEmpty() -> DetGroup(pieces, isBox = false)
            else -> null
        }
        val hits = dedupe(names)
        if (group == null && hits.isEmpty()) return emptyList()
        return listOf(GroupedItem(group, hits.firstOrNull(), hits.drop(1)))
    }

    /** Highest score first, one hit per medicine id. */
    fun dedupe(hits: List<NameHit>): List<NameHit> =
        hits.sortedByDescending { it.topScore }.distinctBy { it.topId }
}
