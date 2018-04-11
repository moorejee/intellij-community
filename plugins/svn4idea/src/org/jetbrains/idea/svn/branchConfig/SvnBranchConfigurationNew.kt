// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil.map
import com.intellij.util.containers.ContainerUtil.sorted
import org.jetbrains.idea.svn.SvnUtil
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.isAncestor
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.util.*
import java.util.Comparator.comparingInt

class SvnBranchConfigurationNew {
  var trunkUrl: String? = ""
  private val myBranchMap: MutableMap<String, InfoStorage<List<SvnBranchItem>>> = mutableMapOf()
  var isUserinfoInUrl: Boolean = false

  val branchUrls: List<String>
    get() {
      val result = ArrayList(myBranchMap.keys)
      val cutList = map<String, String>(result, { cutEndSlash(it) })
      Collections.sort(cutList)
      return cutList
    }

  val branchMap: Map<String, InfoStorage<List<SvnBranchItem>>>
    get() = myBranchMap

  fun addBranches(branchParentName: String, items: InfoStorage<List<SvnBranchItem>>) {
    var branchParentName = branchParentName
    branchParentName = ensureEndSlash(branchParentName)
    val current = myBranchMap[branchParentName]
    if (current != null) {
      LOG.info("Branches list not added for : '$branchParentName; this branch parent URL is already present.")
      return
    }
    myBranchMap[branchParentName] = items
  }

  fun updateBranch(branchParentName: String, items: InfoStorage<List<SvnBranchItem>>) {
    var branchParentName = branchParentName
    branchParentName = ensureEndSlash(branchParentName)
    val current = myBranchMap[branchParentName]
    if (current == null) {
      LOG.info("Branches list not updated for : '$branchParentName; since config has changed.")
      return
    }
    current.accept(items)
  }

  fun getBranches(url: String): List<SvnBranchItem> {
    var url = url
    url = ensureEndSlash(url)
    return myBranchMap[url]?.value ?: emptyList()
  }

  fun copy(): SvnBranchConfigurationNew {
    val result = SvnBranchConfigurationNew()
    result.isUserinfoInUrl = isUserinfoInUrl
    result.trunkUrl = trunkUrl
    for ((key, infoStorage) in myBranchMap) {
      result.myBranchMap.put(key, InfoStorage(ArrayList(infoStorage.value), infoStorage.infoReliability))
    }
    return result
  }

  fun getBaseUrl(url: String): String? {
    if (trunkUrl != null) {
      if (Url.isAncestor(trunkUrl!!, url)) {
        return cutEndSlash(trunkUrl!!)
      }
    }
    for (branchUrl in sortBranchLocations(myBranchMap.keys)) {
      if (Url.isAncestor(branchUrl, url)) {
        val relativePath = Url.getRelative(branchUrl, url)
        val secondSlash = relativePath!!.indexOf("/")
        return cutEndSlash(branchUrl + if (secondSlash == -1) relativePath else relativePath.substring(0, secondSlash))
      }
    }
    return null
  }

  fun getBaseName(url: String): String? {
    val baseUrl = getBaseUrl(url) ?: return null
    val lastSlash = baseUrl.lastIndexOf("/")
    return if (lastSlash == -1) baseUrl else baseUrl.substring(lastSlash + 1)
  }

  fun getRelativeUrl(url: String): String? {
    val baseUrl = getBaseUrl(url)
    return if (baseUrl == null) null else url.substring(baseUrl.length)
  }

  @Throws(SvnBindException::class)
  fun getWorkingBranch(someUrl: Url): Url? {
    val baseUrl = getBaseUrl(someUrl.toString())
    return if (baseUrl == null) null else createUrl(baseUrl)
  }

  @Throws(SvnBindException::class)
  private fun iterateUrls(listener: UrlListener) {
    if (listener.accept(trunkUrl)) {
      return
    }

    for (branchUrl in sortBranchLocations(myBranchMap.keys)) {
      val children = myBranchMap[branchUrl]!!.value
      for (child in children) {
        if (listener.accept(child.url.toDecodedString())) {
          return
        }
      }

      /*if (listener.accept(branchUrl)) {
        return;
      }*/
    }
  }

  // to retrieve mappings between existing in the project working copies and their URLs
  fun getUrl2FileMappings(project: Project, root: VirtualFile): Map<String, String>? {
    try {
      val searcher = BranchRootSearcher(SvnVcs.getInstance(project), root)
      iterateUrls(searcher)
      return searcher.branchesUnder
    }
    catch (e: SvnBindException) {
      return null
    }

  }

  fun removeBranch(url: String) {
    var url = url
    url = ensureEndSlash(url)
    myBranchMap.remove(url)
  }

  private class BranchRootSearcher constructor(vcs: SvnVcs, private val myRoot: VirtualFile) : UrlListener {
    private val myRootUrl: Url?
    // url path to file path
    val myBranchesUnder: MutableMap<String, String>

    val branchesUnder: Map<String, String>
      get() = myBranchesUnder

    init {
      myBranchesUnder = HashMap()
      val info = vcs.getInfo(myRoot.path)
      myRootUrl = info?.url
    }

    @Throws(SvnBindException::class)
    override fun accept(url: String?): Boolean {
      if (myRootUrl != null) {
        val baseDir = virtualToIoFile(myRoot)
        val baseUrl = myRootUrl.path

        val branchUrl = createUrl(url!!)
        if (isAncestor(myRootUrl, branchUrl)) {
          val file = SvnUtil.fileFromUrl(baseDir, baseUrl, branchUrl.path)
          myBranchesUnder[url] = file.absolutePath
        }
      }
      return false // iterate everything
    }
  }

  private interface UrlListener {
    @Throws(SvnBindException::class)
    fun accept(url: String?): Boolean
  }

  companion object {
    private val LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew")

    fun ensureEndSlash(name: String): String {
      return if (name.trim { it <= ' ' }.endsWith("/")) name else "$name/"
    }

    private fun cutEndSlash(name: String): String {
      return if (name.endsWith("/") && name.length > 0) name.substring(0, name.length - 1) else name
    }

    /**
     * Sorts branch locations by length descending as there could be cases when one branch location is under another.
     */
    private fun sortBranchLocations(branchLocations: Collection<String>): Collection<String> {
      return sorted(branchLocations, comparingInt<String>({ it.length }).reversed())
    }
  }
}
