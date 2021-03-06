package example

import diode._
import org.scalajs.dom._

// marker trait to identify actions that should be RAF batched
trait RAFAction

private[example] final case class RAFWrapper(action: AnyRef, dispatch: Dispatcher)

final case class RAFTimeStamp(time: Double)

class RAFBatcher extends ActionProcessor {
  private var batch = List.empty[RAFWrapper]
  private var frameRequested = false

  /**
    * Callback for RAF.
    *
    * @param time Precise time of the frame
    */
  private def nextAnimationFrame(time: Double): Unit = {
    frameRequested = false
    if (batch.nonEmpty) {
      val curBatch = batch
      batch = Nil
      // dispatch all actions in the batch, supports multiple different dispatchers
      curBatch.reverse.groupBy(_.dispatch).foreach { case (dispatch, actions) =>
        // Precede actions with a time stamp action to get correct time in animations.
        // When dispatching a sequence, Circuit optimizes processing internally and only calls
        // listeners after all the actions are processed
        dispatch(RAFTimeStamp(time) :: actions)
      }
      // request next frame
      requestAnimationFrame
    } else {
      // got no actions to dispatch, no need to request next frame
    }
  }

  /**
    * Requests an animation frame from the browser, unless one has already been requested
    */
  private def requestAnimationFrame: Unit = {
    if (!frameRequested) {
      frameRequested = true
      window.requestAnimationFrame(nextAnimationFrame _)
    }
  }

  override def process[M](dispatch: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[M]) = {
    action match {
      case rafAction: RAFAction =>
        // save action into the batch using a wrapper
        batch = RAFWrapper(rafAction, dispatch) :: batch
        // request animation frame to run the batch
        requestAnimationFrame
        // skip processing of the action for now
        ActionResult.NoChange
      case RAFWrapper(rafAction, _) =>
        // unwrap the RAF action and continue processing normally
        next(rafAction)
      case _ =>
        // default is to just call the next processor
        next(action)
    }
  }
}
