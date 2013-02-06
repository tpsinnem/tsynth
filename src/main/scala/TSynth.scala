package tsynth

import scalaz._
import scalaz.effect.ST
import shapeless.{HList, UnaryTCConstraint}
//import scalaz.effect.STArray

//TODO  i will need to figure out if and where and how i'll need laziness to facilitate cyclic references
//  - From what i gather, source/sources etc need to be declared lazy*, and the parameters for them need be
//    introduced under a different name as nullary function values ('by name?').
//   *- rather, i think, they need to _not be (regular) vals_?
//    - for i moment i had them as lazy vals already on the base traits but i'll keep them as defs for now 
//      because i don't want to restrict myself and i don't think this will ultimately cause any trouble

// TODO also have a system with free sinks. Possibly also explicit free sources. Use shapeless.HList?
// - hm seems like i'll need HList anyhow just for basic functionality
trait TBasicSystem[Sources <: HList, Sinks <: HList] {

  val sourceConstraint:UnaryTCConstraint[Sources, TSource]
  val sinkConstraint:UnaryTCConstraint[Sinks, TSink]

  def sources:Sources
  def sinks:Sinks

  //TODO deployment and a bunch of other stuff and thinking probably
  //should probably have a companion that constructs these, with checks that all sinks have source in sources
}

trait TNode {
  
  type NodeImpl[S] <: TNodeImpl[S]

  //TODO think: what exactly should 'deploy' do? is the return type here what i want?
  //      - should deploy in fact be different depending on whether and how many sources a Node has?
  //        - this is connected to the question of whether sources' identities are the only information
  //          that should still need to be filled in at the time of deployment?
  //      - should i even have a deploy method at the node level?
  //      - one thing i know i do need is something that insantiates the Impls. should deploy be it or
  //        have i had some different concepts in mind here. i am confuse D:
  def deploy[S]:ST[S, NodeImpl[S]]

  //TODO !!!! need to figure out how to responsibly [get data out of these]
  //      - so far, all the methods in these are non-ST-monadic! -- and, thankfully, private.
  trait TNodeImpl[S] {
    private[tsynth] def operate:Unit
  }
}

trait TSource[ElemType] extends TNode {

  type NodeImpl[S] <: TSourceImpl[S]

  trait TSourceImpl[S] extends TNodeImpl[S] {
    private[tsynth] def value:ElemType
  }
}

trait TSinkBase extends TNode {
  
  type NodeImpl[S] <: TSinkBaseImpl[S]

  trait TSinkBaseImpl[S] {

    private[tsynth] def readSources:Unit
    // should i care whether, at this level, i should already have the source(s) themselves as members?
  }
}

trait TSink[ElemType] extends TSinkBase {

  type NodeImpl[S] <: TSinkImpl[S]

  def source:TSource[ElemType]

  trait TSinkImpl[S] extends TSinkBaseImpl[S] {
    private[tsynth] def source:TSink.source.NodeImpl[S]
    private[tsynth] var lastRead:ElemType
    private[tsynth] def readSources:Unit { lastRead = source.value }
  }
}

trait TFilterBase[Out] extends TSource[Out] with TSinkBase {

  type NodeImpl[S] <: TFilterBaseImpl[S]

  trait TFilterBaseImpl[S] extends TSourceImpl[S] with TSinkBaseImpl[S]
}

trait TFilter[In, Out] extends TSink[In] with TFilberBase[Out] {

  type NodeImpl[S] <: TFilterImpl[S]

  trait TFilterImpl[S] extends TSinkImpl[S] with TFilterBaseImpl[S]
}

trait TMixer2[In1, In2, Out] extends TFilterBase[Out] {

  type NodeImpl[S] <: TMixer2Impl[S]

  def sources:(TSource[In1], TSource[In2])

  trait TMixer2Impl[S] extends TFilterBaseImpl[S] {
    private[tsynth] def sources:(sources._1.NodeImpl[S], sources._2.NodeImpl[S])
    private[tsynth] val lastRead:VarTuple2[In,Out]
    private[tsynth] def readSources:Unit {
      lastRead._1 = sources._1.value
      lastRead._2 = sources._2.value
    }
  }
}

case class VarTuple2[T1,T2](var _1:T1, var _2:T2)

//trait or abstract class or such?
//TODO keep remembering that this is an initial implementation and will need to be rethought!
//TODO this is actually not exactly what i want. in order to treat filter state separate from output values,
//      i need a separate 'value:ElemType' member that operate writes to. actually probably separate In and Out
//      types. i might ultimately want a completely different [backend] for delay lines, but that might do
//      at least for now, and possibly forever.
//      - actually looks like i'd even want an additional 'internal' type.
trait TCircularArrayFilter[In, Internal, Out] extends TFilter[In, Out] {

  type NodeImpl[S] <: CircularArrayFilterImpl[S]

  val length:Int //TODO think: does this even belong here. shouldn't i instead have a [Seq[?]] member?
                  //    - a seq or somesuch member with a length i can refer to.
  //TODO think, read: i might like some restriction on Array here to prevent user mutation of the array
  val headUpdate: (In, Array[Internal], Int, Int) => Internal
  val valueUpdate: (In, Array[Internal], Int, Int) => Out
  //TODO !!! think of how to facilitate [[variable lengths]] with 'extra length trick'

  trait CircularArrayFilterImpl[S] extends TFilterImpl[S] {
    private[tsynth] val arr:Array[Internal]
    private[tsynth] var value:Out = 0 //TODO think what if anything you actually want here
    private[tsynth] var head = 0
    private[tsynth] var tail = length - 1

    //T0D0 given some [array-specific] function from a [subclass], operate should be definable already
    //  - Do I want the very initial array to be played as such at all? If not, how do I order the
    //    operations? -- hm -- if readSources are done first, would that even be avoidable at all?
    //    - Let it happen. It has more info anyway, so I can have things both ways, adding fixes of top if need

    private[tsynth] def operate {
      //T0D0 should i replace arr(head) here with value? if so, what do i update head with? do i need
      //    to split headUpdate into two separate functions with some different roles?, or just
      //    have another function in addition to the headUpdate as it exists already?
      value = valueUpdate(lastRead, arr, head, tail) //TODO think about ordering !!! !!!
      arr(head) = headUpdate(lastRead, arr, head, tail)
      head = (head + 1) % length
      tail = (tail + 1) % length
    }
    //private[tsynth] def value = arr(head) // replaced with 'value' member
  
    /*
    //T0d0 think:  what should the visibility on this be, if it's to be user-supplied? same question goes
    //      for other such things too, so go through them at some point!
    private[tsynth] def headUpdate( .... */
  }
}

//TODO think: it might be useful to generalize the functionality here, but i'll start with something specific
class TCrudeKarplusStrongDelayLine extends TSource[Float] {

  type NodeImpl[S] = TCrudeKarplusStrongDelayLineImpl[S]
  val delayLine = new DelayLineBuffer
  val lowPass = new CrudeLowPassFilter

  val source = delayLine

  class CrudeLowPassFilter extends TFilter[Float, Float] {
    type NodeImpl[S] = CrudeLowPassFilterImpl[S]
    //TODO think: how do i get sources connected? just source = delayLine?
    trait CrudeLowPassFilterImpl[S] extends TFilterImpl[S] {
      private[tsynth] val source = // delayLine //TODO umm, no?
      private[tsynth] var lastRead:Float = 0.0
      private[tsynth] var value:Float = 0.0
      private[tsynth] var prevRead:Float = 0.0
      private[tsynth] def operate:Unit {
        value = (lastRead + prevRead) / 2.05 // numero ex recto
        prevRead = lastRead
      }
  /*
  class CrudeLowPassFilter extends TCircularArrayFilter[Float, Float, Float] {
    type NodeImpl[S] = CrudeLowPassFilterImpl[S]

    //TODO think: trait or class?
    trait CrudeLowPassFilterImpl[S] extends TCircularArrayFilterImpl[S] {
      private[tsynth] val arr = Array[Float].fill(1)(0.0) //TODO size 1?? is use of TCAF here at all sane?
      private[tsynth] var lastRead:Float = 0.0
      //TODO finish
    }
    //TODO finish
  }
  */
  class DelayLineBuffer extends TCircularArrayFilter[Float, Float, Float] {
    type NodeImpl[S] = DelayLineBufferImpl[S]

    //TODO think: trait or class?
    trait DelayLineBufferImpl[S] extends TCircularArrayFilterImpl[S] {
      private[tsynth] val arr = Array[Float].fill(100)(0.0) //'roughly 440 hertz'
      private[tsynth] var lastRead:Float = 0.0
      //TODO finish
    }
    //TODO finish
  }
  //TODO finish -- deploy? other stuff?
  //  - also, how will TSystemX get 'deployed' and how does it affect how things should work on this level?
}









