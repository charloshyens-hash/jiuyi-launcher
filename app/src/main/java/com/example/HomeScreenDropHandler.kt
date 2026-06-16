package com.example

// 拖拽释放落点处理，从 LauncherHomeScreen 中剥离
// 职责：根据落点坐标决定添加/移动/移除/卸载应用
object HomeScreenDropHandler {

    fun handle(
        viewModel: LauncherViewModel,
        dockPackages: List<String>,
        homePages: List<HomeScreenPage>,
        screenWidth: Int,
        screenHeight: Int,
        isAddScreenOpen: Boolean,
        dragSourcePageIndex: Int,
        showToast: (String) -> Unit
    ) {
        val app = viewModel.draggedApp ?: run {
            resetDragState(viewModel)
            return
        }

        val dropX = viewModel.dragOffset.x
        val dropY = viewModel.dragOffset.y

        when {
            // ── 情形1：AddManagementScreen 缩略图拖拽 ──
            isAddScreenOpen -> {
                var droppedOnThumbnailIndex: Int? = null
                for ((index, rect) in viewModel.addScreenThumbnailBounds) {
                    if (dropX >= rect.left && dropX <= rect.right &&
                        dropY >= rect.top && dropY <= rect.bottom) {
                        droppedOnThumbnailIndex = index
                        break
                    }
                }
                if (droppedOnThumbnailIndex != null) {
                    if (app.packageName.startsWith("WIDGET:")) {
                        val widgetName = app.packageName.substring(7)
                        viewModel.addWidgetToPage(droppedOnThumbnailIndex, widgetName)
                        showToast("已成功添加 $widgetName 到第 ${droppedOnThumbnailIndex + 1} 页")
                    } else {
                        viewModel.addAppToPage(droppedOnThumbnailIndex, app.packageName)
                        showToast("已成功添加快捷方式 ${app.label} 到第 ${droppedOnThumbnailIndex + 1} 页")
                    }
                }
            }

            // ── 情形2：从抽屉拖出 ──
            viewModel.isDraggingFromDrawer -> {
                var droppedOnPage: Int? = null
                for ((idx, rect) in viewModel.drawerThumbnailBounds) {
                    if (dropX >= rect.left && dropX <= rect.right &&
                        dropY >= rect.top && dropY <= rect.bottom) {
                        droppedOnPage = idx
                        break
                    }
                }

                if (droppedOnPage != null) {
                    if (app.packageName.startsWith("WIDGET:")) {
                        val widgetName = app.packageName.substring(7)
                        viewModel.addWidgetToPage(droppedOnPage, widgetName)
                        showToast("已成功将 $widgetName 添加到第 ${droppedOnPage + 1} 页主屏")
                    } else {
                        viewModel.addAppToPage(droppedOnPage, app.packageName)
                        showToast("已成功将 ${app.label} 添加到第 ${droppedOnPage + 1} 页主屏")
                    }
                } else {
                    // 在抽屉内重排序
                    var droppedOnDrawerIndex: Int? = null
                    for ((globalIdx, rect) in viewModel.drawerItemBounds) {
                        if (dropX >= rect.left && dropX <= rect.right &&
                            dropY >= rect.top && dropY <= rect.bottom) {
                            droppedOnDrawerIndex = globalIdx
                            break
                        }
                    }
                    if (droppedOnDrawerIndex == null) {
                        var minDistance = Float.MAX_VALUE
                        var closestIdx: Int? = null
                        for ((globalIdx, rect) in viewModel.drawerItemBounds) {
                            val centerX = (rect.left + rect.right) / 2f
                            val centerY = (rect.top + rect.bottom) / 2f
                            val dist = (dropX - centerX) * (dropX - centerX) + (dropY - centerY) * (dropY - centerY)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestIdx = globalIdx
                            }
                        }
                        if (minDistance < 20000f) droppedOnDrawerIndex = closestIdx
                    }

                    if (droppedOnDrawerIndex != null) {
                        viewModel.reorderDrawerApp(app.packageName, droppedOnDrawerIndex)
                        showToast("已调整应用抽屉图标排序")
                    } else {
                        showToast("请将图标/组件挪动至主屏幕缩略图上以完成添加")
                    }
                }
            }

            // ── 情形3：桌面内部拖拽 ──
            else -> {
                val isOverTopBar = dropY <= 100f && dropY > 0f
                if (isOverTopBar) {
                    if (dropX < screenWidth / 2f) {
                        if (viewModel.isDraggingFromDock) {
                            val currentDockList = dockPackages.toMutableList()
                            if (viewModel.dragSourceIndex in currentDockList.indices) {
                                currentDockList.removeAt(viewModel.dragSourceIndex)
                            }
                            viewModel.updateDockConfiguration(currentDockList)
                            showToast("已移除 ${app.label} 快捷图标")
                        } else {
                            viewModel.removeAppFromPage(viewModel.activePageIndex.value, app.packageName)
                            showToast("已从桌面移除 ${app.label} 快捷图标")
                        }
                    } else {
                        viewModel.uninstallApp(
                            context = null,
                            app = app
                        )
                    }
                } else {
                    val isOverDockZone = dropY >= (screenHeight - 160)
                    if (isOverDockZone) {
                        handleDropOnDock(
                            viewModel, app, dockPackages, dropX, screenWidth, showToast
                        )
                    } else {
                        handleDropOnHomeGrid(
                            viewModel, app, dockPackages, dropX, dropY,
                            dragSourcePageIndex, screenWidth, showToast
                        )
                    }
                }
            }
        }

        resetDragState(viewModel)
    }

    private fun handleDropOnDock(
        viewModel: LauncherViewModel,
        app: AppModel,
        dockPackages: List<String>,
        dropX: Float,
        screenWidth: Int,
        showToast: (String) -> Unit
    ) {
        val currentDockList = dockPackages.toMutableList()
        if (viewModel.isDraggingFromDock && viewModel.dragSourceIndex in currentDockList.indices) {
            currentDockList.removeAt(viewModel.dragSourceIndex)
        } else {
            if (!app.packageName.startsWith("WIDGET:") && app.packageName != "MENU_BUTTON") {
                viewModel.removeAppFromPage(viewModel.activePageIndex.value, app.packageName)
            }
        }
        val itemsCount = currentDockList.size
        val dockMaxWidth = 500f
        val dockActualWidthDp = screenWidth.coerceAtMost(dockMaxWidth.toInt()) - 24f
        val dockStartX = (screenWidth - dockActualWidthDp) / 2f
        val activeStartX = dockStartX + 8f
        val activeWidth = dockActualWidthDp - 16f
        val cellWidthDp = if (itemsCount > 0) activeWidth / itemsCount else 68f
        val targetIndex = if (itemsCount > 0) {
            ((dropX - activeStartX) / cellWidthDp).toInt().coerceIn(0, itemsCount)
        } else 0

        if (app.packageName == "MENU_BUTTON") {
            currentDockList.add(targetIndex, "MENU_BUTTON")
        } else {
            currentDockList.removeAll { it == app.packageName }
            currentDockList.add(targetIndex, app.packageName)
        }
        viewModel.updateDockConfiguration(currentDockList)
        showToast("已调整 Dock 快捷排列")
    }

    private fun handleDropOnHomeGrid(
        viewModel: LauncherViewModel,
        app: AppModel,
        dockPackages: List<String>,
        dropX: Float,
        dropY: Float,
        dragSourcePageIndex: Int,
        screenWidth: Int,
        showToast: (String) -> Unit
    ) {
        var targetSlotIndex: Int? = null
        for ((cellIdx, rect) in viewModel.homeGridBounds) {
            if (dropX >= rect.left && dropX <= rect.right &&
                dropY >= rect.top && dropY <= rect.bottom) {
                targetSlotIndex = cellIdx
                break
            }
        }
        if (targetSlotIndex == null) {
            var minDistance = Float.MAX_VALUE
            var closestIdx: Int? = null
            for ((cellIdx, rect) in viewModel.homeGridBounds) {
                val centerX = (rect.left + rect.right) / 2f
                val centerY = (rect.top + rect.bottom) / 2f
                val dist = (dropX - centerX) * (dropX - centerX) + (dropY - centerY) * (dropY - centerY)
                if (dist < minDistance) {
                    minDistance = dist
                    closestIdx = cellIdx
                }
            }
            if (minDistance < 25000f) targetSlotIndex = closestIdx
        }

        val currentActivePageIdx = viewModel.activePageIndex.value
        val isCrossPage = !viewModel.isDraggingFromDock && currentActivePageIdx != dragSourcePageIndex

        if (targetSlotIndex != null) {
            when {
                viewModel.isDraggingFromDock -> {
                    val currentDockList = dockPackages.toMutableList()
                    if (viewModel.dragSourceIndex in currentDockList.indices) {
                        currentDockList.removeAt(viewModel.dragSourceIndex)
                    }
                    viewModel.updateDockConfiguration(currentDockList)
                    viewModel.addAppToPageAtSlot(currentActivePageIdx, app.packageName, targetSlotIndex)
                    showToast("已将 ${app.label} 移至桌面指定位置")
                }
                isCrossPage -> {
                    viewModel.removeAppFromPage(dragSourcePageIndex, app.packageName)
                    viewModel.addAppToPageAtSlot(currentActivePageIdx, app.packageName, targetSlotIndex)
                    showToast("已将 ${app.label} 移至第 ${currentActivePageIdx + 1} 页")
                }
                else -> {
                    if (viewModel.dragSourceIndex != -1) {
                        viewModel.moveAppInPage(currentActivePageIdx, viewModel.dragSourceIndex, targetSlotIndex)
                        showToast("已调整 ${app.label} 的位置")
                    }
                }
            }
        } else {
            when {
                viewModel.isDraggingFromDock -> {
                    if (app.packageName == "MENU_BUTTON") {
                        showToast("菜单按钮不能移除！")
                    } else {
                        val currentDockList = dockPackages.toMutableList()
                        if (viewModel.dragSourceIndex in currentDockList.indices) {
                            currentDockList.removeAt(viewModel.dragSourceIndex)
                        }
                        viewModel.updateDockConfiguration(currentDockList)
                        showToast("已从 Dock 移除: ${app.label}")
                    }
                }
                isCrossPage -> {
                    viewModel.removeAppFromPage(dragSourcePageIndex, app.packageName)
                    viewModel.addAppToPage(currentActivePageIdx, app.packageName)
                    showToast("已将 ${app.label} 移至第 ${currentActivePageIdx + 1} 页")
                }
            }
        }
    }

    private fun resetDragState(viewModel: LauncherViewModel) {
        viewModel.draggedApp = null
        viewModel.isDraggingActive = false
        viewModel.isDraggingFromDock = false
        viewModel.isDraggingFromDrawer = false
        viewModel.dragSourceIndex = -1
        viewModel.isEditingHomeScreen = false
    }
}