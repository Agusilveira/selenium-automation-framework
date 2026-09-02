# language: es
Característica: Login en SauceDemo

  Los mismos casos que la suite de TestNG, escritos en Gherkin y ejecutados
  sobre las mismas páginas y el mismo WebUI. El framework no está casado con
  un runner.

  Antecedentes:
    Dado que estoy en la página de login

  @smoke
  Escenario: Un usuario válido accede al inventario
    Cuando ingreso con "standard_user"
    Entonces veo la lista de productos

  Escenario: Un usuario bloqueado no puede entrar
    Cuando ingreso con "locked_out_user"
    Entonces veo un mensaje de error que contiene "locked out"

  Esquema del escenario: Distintos usuarios, distinto resultado
    Cuando ingreso con "<usuario>" y la clave "<clave>"
    Entonces el resultado es "<resultado>"

    Ejemplos:
      | usuario         | clave        | resultado |
      | standard_user   | secret_sauce | exito     |
      | locked_out_user | secret_sauce | error     |
      | standard_user   | clave_mala   | error     |
