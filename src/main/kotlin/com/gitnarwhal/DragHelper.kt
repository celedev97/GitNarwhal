package com.gitnarwhal

import javafx.event.EventHandler
import javafx.scene.Parent
import javafx.scene.input.MouseEvent
import javafx.stage.Stage

class DragHelper(stage: Stage, handle: Parent) {
    var xOffset = .0
    var yOffset = .0


    val mouseDragged = EventHandler<MouseEvent>{ event ->
        stage.x = event.screenX + xOffset;
        stage.y = event.screenY + yOffset;
    }
    val mousePressed = EventHandler<MouseEvent>{event ->
        xOffset = stage.x - event.screenX;
        yOffset = stage.y - event.screenY;
    }


    init {
        handle.onMousePressed = mousePressed;
        handle.onMouseDragged = mouseDragged;
    }




}