package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.EditorState.{currentTiling, strictness}
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.{AsyncUtils, TilingGenerator, UndoManager}
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, TilingError, ValidationError, VertexId}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.concurrent.ExecutionContext.Implicits.global

object TessellationOperations:

  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      EditorState.selectedPolygon.set(Some(sides))

      if currentTiling.now().isEmpty then
        UndoManager.saveState()
        TilingGenerator.createTilingFromPolygon(sides) match
          case Some(tiling) =>
            currentTiling.set(tiling)
            SelectionOperations.clearAllSelections()
          case None =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  def clearTiling(): Unit =
    ifNotProcessing:
      if !currentTiling.now().isEmpty then
        UndoManager.saveState()

      currentTiling.set(TilingDCEL.empty)
      EditorState.polygonColors.set(Map.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  // Attempt to delete a polygon from the tessellation
  def attemptPolygonDeletion(polygonId: String): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            // Extract polygon tag from the ID
            val polyTag = if polygonId.startsWith("tiling-poly-") then
              polygonId.substring("tiling-poly-".length)
            else
              polygonId
            // Find the specific polygon in the tiling
//            val targetPolygon = tiling.orientedPolygons.find { nodes =>
//              val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
//              tag == polyTag
//            }
            val targetPolygon = tiling.innerFaces.find { face =>
              val tag = face.id.value
              tag == polyTag
            }

            targetPolygon match
              case Some(face) =>
                // All checks passed - try actual deletion
                val result: Either[TilingError, TilingDCEL] =
                  tiling.maybeDeleteFace(face.id)
                result match
                  case Right(newTiling) =>
                    // Success: return the new tiling
                    Right(newTiling)
                  case Left(errMsg) =>
                    // Failure: return error with wireframe info
                    Left(s"Cannot remove polygon: ${errMsg.message}")

              case None =>
                Left(s"Could not find polygon with tag: $polyTag")

          case _ =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        currentTiling.set(newTiling)
        ErrorOperations.clearError()
      case Left(errMsg) =>
        // Failure: show error with wireframe info
        val polygonNodes = currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            val polyTag = if polygonId.startsWith("tiling-poly-") then
              polygonId.substring("tiling-poly-".length)
            else
              polygonId

//            tiling.orientedPolygons.find { nodes =>
//              val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
//              tag == polyTag
//            }.getOrElse(Vector.empty)
          case _ => Vector.empty

//        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes)
//        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Attempt to delete a polygon from the tessellation
  def attemptFaceDeletion(faceId: FaceId): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            // All checks passed - try actual deletion
            val result: Either[TilingError, TilingDCEL] =
              tiling.maybeDeleteFace(faceId)
            result match
              case Right(newTiling) =>
                // Success: return the new tiling
                Right(newTiling)
              case Left(errMsg) =>
                // Failure: return error with wireframe info
                Left(s"Cannot remove polygon: ${errMsg.message}")
          case _ =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        currentTiling.set(newTiling)
        ErrorOperations.clearError()
      case Left(errMsg) =>
        // Failure: show error with wireframe info
        val polygonNodes = currentTiling.now() match
          case tiling if !tiling.isEmpty =>
//            val polyTag = if polygonId.startsWith("tiling-poly-") then
//              polygonId.substring("tiling-poly-".length)
//            else
//              polygonId

          //            tiling.orientedPolygons.find { nodes =>
          //              val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
          //              tag == polyTag
          //            }.getOrElse(Vector.empty)
          case _ => Vector.empty

      //        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes)
      //        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Attempt to delete a polygon from the tessellation
  def attemptVertexDeletion(vertexId: VertexId): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case tiling if !tiling.isEmpty =>
                // All checks passed - try actual deletion
                val result: Either[TilingError, TilingDCEL] =
                  tiling.maybeDeleteVertex(vertexId)
                result match
                  case Right(newTiling) =>
                    // Success: return the new tiling
                    Right(newTiling)
                  case Left(errMsg) =>
                    // Failure: return error with wireframe info
                    Left(s"Cannot remove vertex: ${errMsg.message}")

          case _ =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        currentTiling.set(newTiling)
        ErrorOperations.clearError()
      case Left(errMsg) =>
        // Failure: show error with wireframe info
//        val polygonNodes = currentTiling.now() match
//          case tiling if !tiling.isEmpty =>
//            val polyTag = if polygonId.startsWith("tiling-poly-") then
//              polygonId.substring("tiling-poly-".length)
//            else
//              polygonId
//
//          //            tiling.orientedPolygons.find { nodes =>
//          //              val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
//          //              tag == polyTag
//          //            }.getOrElse(Vector.empty)
//          case _ => Vector.empty

      //        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes)
      //        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Attempt to delete a polygon from the tessellation
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            // All checks passed - try actual deletion
            val result: Either[TilingError, TilingDCEL] =
              tiling.maybeDeleteEdge(startVertexId, endVertexId)
            result match
              case Right(newTiling) =>
                // Success: return the new tiling
                Right(newTiling)
              case Left(errMsg) =>
                // Failure: return error with wireframe info
                Left(s"Cannot remove edge: ${errMsg.message}")

          case _ =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        currentTiling.set(newTiling)
        ErrorOperations.clearError()
      case Left(errMsg) =>
      // Failure: show error with wireframe info
      //        val polygonNodes = currentTiling.now() match
      //          case tiling if !tiling.isEmpty =>
      //            val polyTag = if polygonId.startsWith("tiling-poly-") then
      //              polygonId.substring("tiling-poly-".length)
      //            else
      //              polygonId
      //
      //          //            tiling.orientedPolygons.find { nodes =>
      //          //              val tag = nodes.sorted(using NodeOrdering).map(_.toString).mkString("-")
      //          //              tag == polyTag
      //          //            }.getOrElse(Vector.empty)
      //          case _ => Vector.empty

      //        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes)
      //        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Handle perimeter edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now()) match
      case (tiling, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available to grow")
      case (tiling, Some(polygonSides)) =>
        // Try to grow the edge with the selected polygon
        AsyncUtils.withLoadingState { () =>
          try
            import io.github.scala_tessella.tessella.RegularPolygon.Polygon
            val polygon = Polygon(polygonSides)
            val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.id).slidingO(2).toList

            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head, polygonSides)
            else
              Left(ValidationError("Invalid edge index"))
          catch
            case e: Exception => Left(ValidationError(s"Error growing edge: ${e.getMessage}"))
        }.foreach {
          case Right(newTiling) =>
            // Success: save state before change, then update tiling
            UndoManager.saveState()
            currentTiling.set(newTiling)
            EditorState.selectedPerimeterEdges.set(Set.empty)
            ErrorOperations.clearError()
          case Left(errMsg) =>
            // Failure: show error message with wireframe (no state to undo)
            val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.id).slidingO(2).toList
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val placement = FailedPolygonPlacement(edgeIndex, polygonSides, (selectedEdge(0), selectedEdge(1)), tiling)
              val truncated = errMsg.message
//                val idx = errMsg.indexOf("See SVG")
//                if idx >= 0 then errMsg.substring(0, idx)
//                else errMsg
              ErrorOperations.showError(s"Growing ${polygonName(polygonSides)}s on this perimeter edge is invalid. Switch Validation OFF to proceed. $truncated", Some(placement))
            else
              ErrorOperations.showError(errMsg.message)
        }
      case (_, None) =>
        // This case should be handled by the caller
        ()