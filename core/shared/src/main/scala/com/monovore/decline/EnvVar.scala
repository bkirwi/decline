package com.monovore.decline

/**
 * The default behaviour of an environment variable when it is absent from the environment.
 */
sealed trait EnvVarDefault
object EnvVarDefault {

  /** The variable must be set; parsing fails if it is absent. */
  case object Required extends EnvVarDefault

  /**
   * The variable is optional. Produced by `.orNone`, `.orEmpty`, `.orFalse`, or `.orTrue`;
   * absent values yield `None`, `Nil`, `false`, or `true` respectively.
   */
  case object Optional extends EnvVarDefault

  /** The variable has a concrete default string that is used when it is absent. */
  final case class Value(value: String) extends EnvVarDefault
}

/**
 * Metadata for a single environment variable declared in an [[Opts]] tree.
 *
 * Obtained via [[Help.envVars]].
 */
final case class EnvVar(name: String, metavar: String, help: String, default: EnvVarDefault) {
  private[decline] def show(theme: Theme): List[String] = {
    import RenderUtils._
    val defaultSuffix = default match {
      case EnvVarDefault.Required     => ""
      case EnvVarDefault.Optional     => " (optional)"
      case EnvVarDefault.Value(value) => s" (default: $value)"
    }
    List(theme.envName(name) + s"=<$metavar>", withIndent(4, help + defaultSuffix))
  }
}
