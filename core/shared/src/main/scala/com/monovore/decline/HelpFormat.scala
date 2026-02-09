package com.monovore.decline

class HelpFormat private (opts: Flags) {

  /**
   * Toggle rendering of colors
   *
   * @param colors
   * @return
   */
  def withColors(colors: Boolean): HelpFormat = copy(_.copy(colors = colors))

  /**
   * Toggle rendering of commands section
   *
   * @param enabled
   * @return
   */
  def withCommands(enabled: Boolean): HelpFormat = copy(_.copy(commands = enabled))

  /**
   * Toggle rendering of options section
   *
   * @param enabled
   * @return
   */
  def withOptions(enabled: Boolean): HelpFormat = copy(_.copy(options = enabled))

  /**
   * Toggle rendering of environment variables section
   *
   * @param enabled
   * @return
   */
  def withEnv(enabled: Boolean): HelpFormat = copy(_.copy(env = enabled))

  /**
   * Toggle rendering of usage section
   *
   * @param enabled
   * @return
   */
  def withUsage(enabled: Boolean): HelpFormat = copy(_.copy(usage = enabled))

  /**
   * Toggle rendering of errors section
   *
   * @param enabled
   * @return
   */
  def withErrors(enabled: Boolean): HelpFormat = copy(_.copy(errors = enabled))

  /**
   * Toggle rendering of description section
   *
   * @param enabled
   * @return
   */
  def withDescription(enabled: Boolean): HelpFormat = copy(_.copy(description = enabled))

  /**
   * Whether commands section should be rendered
   *
   * @return
   */
  def commandsEnabled: Boolean = opts.commands

  /**
   * Whether options section should be rendered
   *
   * @return
   */
  def optionsEnabled: Boolean = opts.options

  /**
   * Whether environment variables section should be rendered
   *
   * @return
   */
  def envEnabled: Boolean = opts.env

  /**
   * Whether usage section should be rendered
   *
   * @return
   */
  def usageEnabled: Boolean = opts.usage

  /**
   * Whether errors section should be rendered
   *
   * @return
   */
  def errorsEnabled: Boolean = opts.errors

  /**
   * Whether description section should be rendered
   *
   * @return
   */
  def descriptionEnabled: Boolean = opts.description

  /**
   * Whether colors should be used in the help output
   *
   * @return
   */
  def colorsEnabled: Boolean = opts.colors

  private def copy(f: Flags => Flags) = new HelpFormat(f(opts))
}

object HelpFormat {

  def apply(): HelpFormat = new HelpFormat(Flags())

  /**
   * Format help with no colors
   */
  case object Plain extends HelpFormat(Flags(colors = false))

  /**
   * Format help with colors
   */
  case object Colors extends HelpFormat(Flags(colors = true))

  /**
   * Format that disables colors when a `NO_COLOR` environment variable is present.
   *
   * Example usage: `autoColors(sys.env)`
   *
   * @param env
   * @return
   */
  def autoColors(env: Map[String, String]) =
    Plain.withColors(colors = env.get("NO_COLOR").map(_ => false).getOrElse(true))
}

private[decline] case class Flags(
    commands: Boolean = true,
    options: Boolean = true,
    env: Boolean = true,
    usage: Boolean = true,
    errors: Boolean = true,
    description: Boolean = true,
    colors: Boolean = true
)
