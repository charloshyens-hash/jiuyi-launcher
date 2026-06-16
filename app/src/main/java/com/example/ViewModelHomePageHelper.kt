package com.example

// ═══════════════════════════════════════════════════════════════════════════
//  桌面页 / Dock / HomePage 管理扩展方法（从 LauncherViewModel 拆分，功能与签名完全不变）
// ═══════════════════════════════════════════════════════════════════════════

fun LauncherViewModel.loadHomePages() {
    val raw = prefs.homePagesRaw
    if (raw.isEmpty()) {
        val defaultPage = HomeScreenPage("0", apps = List(24) { "EMPTY" }, widgets = listOf("RAM Booster", "Music Cassette"))
        homePages.value = listOf(defaultPage)
    } else {
        val parts = raw.split("|||")
        homePages.value = parts.mapIndexed { index, part ->
            val subParts = part.split(":::")
            var apps = emptyList<String>()
            var widgets = emptyList<String>()
            for (sub in subParts) {
                if (sub.startsWith("apps:"))    apps    = sub.substring(5).split(",")
                else if (sub.startsWith("widgets:")) widgets = sub.substring(8).split(",").filter { it.isNotEmpty() }
            }
            val mutableApps = apps.toMutableList()
            while (mutableApps.size < 24) { mutableApps.add("EMPTY") }
            if (mutableApps.size > 24) { mutableApps.subList(24, mutableApps.size).clear() }
            HomeScreenPage(index.toString(), mutableApps, widgets)
        }
    }
}

fun LauncherViewModel.saveHomePages(pages: List<HomeScreenPage>) {
    val serialized = pages.joinToString("|||") { page ->
        val normalizedApps = page.apps.toMutableList()
        while (normalizedApps.size < 24) { normalizedApps.add("EMPTY") }
        if (normalizedApps.size > 24) { normalizedApps.subList(24, normalizedApps.size).clear() }
        "apps:${normalizedApps.joinToString(",")}:::widgets:${page.widgets.joinToString(",")}"
    }
    prefs.homePagesRaw = serialized
    homePages.value = pages
}

fun LauncherViewModel.addHomePage() {
    val current = homePages.value.toMutableList()
    current.add(HomeScreenPage(current.size.toString()))
    saveHomePages(current)
}

fun LauncherViewModel.deleteHomePage(index: Int) {
    val current = homePages.value.toMutableList()
    if (index in current.indices) {
        current.removeAt(index)
        val reindexed = current.mapIndexed { reIndex, page -> HomeScreenPage(reIndex.toString(), page.apps, page.widgets) }
        saveHomePages(reindexed)
        if (activePageIndex.value >= reindexed.size) activePageIndex.value = maxOf(0, reindexed.size - 1)
    }
}

fun LauncherViewModel.reorderHomePage(fromIndex: Int, toIndex: Int) {
    val current = homePages.value.toMutableList()
    if (fromIndex in current.indices && toIndex in current.indices) {
        val page = current.removeAt(fromIndex)
        current.add(toIndex, page)
        saveHomePages(current.mapIndexed { reIndex, p -> HomeScreenPage(reIndex.toString(), p.apps, p.widgets) })
    }
}

fun LauncherViewModel.addAppToPage(pageIndex: Int, packageName: String) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        val pageApps = page.apps.toMutableList()
        while (pageApps.size < 24) { pageApps.add("EMPTY") }
        if (!pageApps.contains(packageName)) {
            val emptyIdx = pageApps.indexOf("EMPTY")
            if (emptyIdx != -1) pageApps[emptyIdx] = packageName else pageApps.add(packageName)
            current[pageIndex] = page.copy(apps = pageApps)
            saveHomePages(current)
        }
    }
}

fun LauncherViewModel.addAppToPageAtSlot(pageIndex: Int, packageName: String, slotIndex: Int) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        val pageApps = page.apps.toMutableList()
        while (pageApps.size < 24) { pageApps.add("EMPTY") }
        if (slotIndex in pageApps.indices) {
            val existingIdx = pageApps.indexOf(packageName)
            if (existingIdx != -1) pageApps[existingIdx] = "EMPTY"
            val oldApp = pageApps[slotIndex]
            pageApps[slotIndex] = packageName
            if (oldApp != "EMPTY" && oldApp != packageName) {
                val emptyIdx = pageApps.indexOf("EMPTY")
                if (emptyIdx != -1) pageApps[emptyIdx] = oldApp
            }
            current[pageIndex] = page.copy(apps = pageApps)
            saveHomePages(current)
        }
    }
}

fun LauncherViewModel.moveAppInPage(pageIndex: Int, fromSlotIndex: Int, toSlotIndex: Int) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        val pageApps = page.apps.toMutableList()
        while (pageApps.size < 24) { pageApps.add("EMPTY") }
        if (fromSlotIndex in pageApps.indices && toSlotIndex in pageApps.indices) {
            val temp = pageApps[fromSlotIndex]
            pageApps[fromSlotIndex] = pageApps[toSlotIndex]
            pageApps[toSlotIndex] = temp
            current[pageIndex] = page.copy(apps = pageApps)
            saveHomePages(current)
        }
    }
}

fun LauncherViewModel.removeAppFromPage(pageIndex: Int, packageName: String) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        current[pageIndex] = page.copy(apps = page.apps.map { if (it == packageName) "EMPTY" else it })
        saveHomePages(current)
    }
}

fun LauncherViewModel.addWidgetToPage(pageIndex: Int, widgetName: String) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        if (!page.widgets.contains(widgetName)) {
            current[pageIndex] = page.copy(widgets = page.widgets + widgetName)
            saveHomePages(current)
        }
    }
}

fun LauncherViewModel.removeWidgetFromPage(pageIndex: Int, widgetName: String) {
    val current = homePages.value.toMutableList()
    if (pageIndex in current.indices) {
        val page = current[pageIndex]
        current[pageIndex] = page.copy(widgets = page.widgets - widgetName)
        saveHomePages(current)
    }
}

fun LauncherViewModel.loadDockConfiguration() {
    val raw = prefs.dockPackagesCommaSeparated
    dockPackages.value = raw.split(",").filter { it.isNotEmpty() && it != "EMPTY" }
}

fun LauncherViewModel.updateDockConfiguration(newList: List<String>) {
    val cleanList = newList.filter { it.isNotEmpty() && it != "EMPTY" }
    prefs.dockPackagesCommaSeparated = cleanList.joinToString(",")
    dockPackages.value = cleanList
}

fun LauncherViewModel.swapOrUpdateDockItem(index: Int, targetPackage: String) {
    val current = dockPackages.value.toMutableList()
    val indexInDock = current.indexOf(targetPackage)
    if (indexInDock != -1) {
        val temp = current.getOrNull(index)
        if (temp != null && temp != "MENU_BUTTON" && targetPackage != "MENU_BUTTON") {
            current[index] = targetPackage; current[indexInDock] = temp
        }
    } else {
        if (index in 0..current.size) current.add(index, targetPackage) else current.add(targetPackage)
    }
    updateDockConfiguration(current)
}

fun LauncherViewModel.removeDockItem(index: Int) {
    val current = dockPackages.value.toMutableList()
    if (index in 0 until current.size && current[index] != "MENU_BUTTON") {
        current.removeAt(index); updateDockConfiguration(current)
    }
}

fun LauncherViewModel.handleDockDrop(app: AppModel, targetIndex: Int?) {
    val current = dockPackages.value.toMutableList()
    val existingIndex = current.indexOf(app.packageName)
    if (targetIndex != null) {
        val safeTarget = targetIndex.coerceIn(0, current.size)
        if (existingIndex != -1) {
            current.removeAt(existingIndex)
            val newTarget = if (safeTarget > existingIndex) safeTarget - 1 else safeTarget
            current.add(newTarget.coerceIn(0, current.size), app.packageName)
        } else { current.add(safeTarget, app.packageName) }
    } else {
        if (existingIndex != -1 && app.packageName != "MENU_BUTTON") current.removeAt(existingIndex)
    }
    updateDockConfiguration(current)
}
