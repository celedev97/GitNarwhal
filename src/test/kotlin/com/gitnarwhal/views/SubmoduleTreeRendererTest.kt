package com.gitnarwhal.views

import com.gitnarwhal.views.RepoTab.SubmoduleInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Guards the submodule tree renderer — which lives in `views/` and is therefore
 * excluded from the coverage metric, so it had no test. Headless-safe: builds the
 * tree model + renderer directly, no display required.
 *
 * Regression: the folder-node branch iterated a consume-once
 * `breadthFirstEnumeration().asSequence()` twice, throwing IllegalStateException
 * during layout. These tests render a folder node (with children) and every leaf
 * state, so that class of bug fails the build instead of the app.
 */
class SubmoduleTreeRendererTest {

    private fun sub(name: String, dirty: Boolean = false, diff: Boolean = false, uninit: Boolean = false) =
        SubmoduleInfo("libs/$name", name, "abc1234", dirty, diff, uninit)

    private fun render(node: DefaultMutableTreeNode) {
        val renderer = RepoTab.SubmoduleTreeRenderer()
        val tree = JTree(DefaultTreeModel(node))
        // The exact flags don't matter; we only care that rendering doesn't throw.
        renderer.getTreeCellRendererComponent(tree, node, false, false, node.isLeaf, 0, false)
    }

    @Test
    fun `folder node with mixed children renders without throwing`() {
        val folder = DefaultMutableTreeNode("libs")
        folder.add(DefaultMutableTreeNode(sub("alpha", dirty = true)))
        folder.add(DefaultMutableTreeNode(sub("beta", diff = true)))
        folder.add(DefaultMutableTreeNode(sub("gamma")))
        // Pre-fix this threw "This sequence can be consumed only once."
        assertDoesNotThrow { render(folder) }
    }

    @Test
    fun `leaf nodes render for every submodule state`() {
        listOf(
            sub("clean"),
            sub("dirtyOne",  dirty = true),
            sub("diffOne",   diff = true),
            sub("uninitOne", uninit = true),
        ).forEach { info ->
            assertDoesNotThrow({ render(DefaultMutableTreeNode(info)) }, "state ${info.name} should render")
        }
    }

    @Test
    fun `needsAttention reflects dirty or different-commit`() {
        assertTrue(sub("a", dirty = true).needsAttention)
        assertTrue(sub("b", diff = true).needsAttention)
        assertFalse(sub("c").needsAttention)
        assertFalse(sub("d", uninit = true).needsAttention)
    }
}
