/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.library.bookmarks

import android.content.Context
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.concept.storage.BookmarkNode
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components

class DesktopFolders(
    context: Context,
    private val showMobileRoot: Boolean
) {

    private val bookmarksStorage = context.components.core.bookmarksStorage
    private val accountManager = context.components.backgroundServices.accountManager

    private val bookmarksTitle = context.getString(R.string.library_bookmarks)

    /**
     * Map of [BookmarkNode.title] to translated strings.
     */
    private val rootTitles: Map<String, String> = if (showMobileRoot) {
        mapOf(
            "root" to bookmarksTitle,
            "mobile" to bookmarksTitle,
            "menu" to context.getString(R.string.library_desktop_bookmarks_menu),
            "toolbar" to context.getString(R.string.library_desktop_bookmarks_toolbar),
            "unfiled" to context.getString(R.string.library_desktop_bookmarks_unfiled)
        )
    } else {
        mapOf(
            "root" to context.getString(R.string.library_desktop_bookmarks_root),
            "menu" to context.getString(R.string.library_desktop_bookmarks_menu),
            "toolbar" to context.getString(R.string.library_desktop_bookmarks_toolbar),
            "unfiled" to context.getString(R.string.library_desktop_bookmarks_unfiled)
        )
    }

    fun withRootTitle(node: BookmarkNode): BookmarkNode =
        if (rootTitles.containsKey(node.title)) node.copy(title = rootTitles[node.title]) else node

    suspend fun withOptionalDesktopFolders(node: BookmarkNode): BookmarkNode {
        val loggedIn = accountManager.authenticatedAccount() != null

        return when (node.guid) {
            BookmarkRoot.Mobile.id -> if (loggedIn) {
                // We're going to make a copy of the mobile node, and add-in a synthetic child folder to the top of the
                // children's list that contains all of the desktop roots.
                val childrenWithVirtualFolder =
                    listOfNotNull(virtualDesktopFolder()) + node.children.orEmpty()

                node.copy(children = childrenWithVirtualFolder)
            } else {
                node
            }
            BookmarkRoot.Root.id ->
                node.copy(
                    title = rootTitles[node.title],
                    children = if (showMobileRoot) {
                        restructureMobileRoots(node.children)
                    } else {
                        restructureDesktopRoots(node.children)
                    }
                )
            BookmarkRoot.Menu.id, BookmarkRoot.Toolbar.id, BookmarkRoot.Unfiled.id ->
                // If we're looking at one of the desktop roots, change their titles to friendly names.
                node.copy(title = rootTitles[node.title])
            else ->
                // Otherwise, just return the node as-is.
                node
        }
    }

    private suspend fun virtualDesktopFolder(): BookmarkNode? {
        val rootNode = bookmarksStorage.getTree(BookmarkRoot.Root.id, recursive = false) ?: return null
        return rootNode.copy(title = rootTitles[rootNode.title])
    }

    /**
     * Removes 'mobile' root (to avoid a cyclical bookmarks tree in the UI) and renames other roots to friendly titles.
     */
    private fun restructureDesktopRoots(roots: List<BookmarkNode>?): List<BookmarkNode>? {
        roots ?: return null

        return roots.filter { rootTitles.containsKey(it.title) }
            .map { it.copy(title = rootTitles[it.title]) }
    }

    /**
     * Restructures roots to place desktop roots underneath the mobile root and renames them to friendly titles.
     * This provides a recognizable bookmark tree when offering destinations to move a bookmark.
     */
    private fun restructureMobileRoots(roots: List<BookmarkNode>?): List<BookmarkNode>? {
        roots ?: return null

        val loggedIn = accountManager.authenticatedAccount() != null

        val others = if (loggedIn) {
            roots.filter { it.guid != BookmarkRoot.Mobile.id }
                .map { it.copy(title = rootTitles[it.title]) }
        } else {
            emptyList()
        }

        val mobileRoot = roots.find { it.guid == BookmarkRoot.Mobile.id } ?: return roots
        val mobileChildren = others + mobileRoot.children.orEmpty()

        // Note that the desktop bookmarks folder does not appear because it is not selectable as a parent
        return listOf(
            mobileRoot.copy(
                children = mobileChildren,
                title = bookmarksTitle
            )
        )
    }
}
