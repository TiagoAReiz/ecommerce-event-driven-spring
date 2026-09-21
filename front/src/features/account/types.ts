/** Tipos da area de conta. So o que o contrato de `user` devolve/aceita. */

/** `GET /users/me` — perfil completo do dono do token. */
export type UserProfile = {
  id: number
  name: string
  email: string
  cpf: string | null
  phone: string | null
  photoUrl: string | null
  roles: string[]
  addressCount: number
  createdAt: string
  updatedAt: string
}

/** Campos que `PATCH /users/me` aceita. Ao menos um precisa vir preenchido. */
export type ProfilePatch = {
  name?: string
  cpf?: string
  phone?: string
  photoUrl?: string
}

/** Endereco de entrega, como a API devolve. */
export type Address = {
  id: number
  name: string | null
  zipcode: string
  country: string
  state: string
  city: string
  street: string
  number: string | null
  createdAt: string
}

/** Corpo de `POST`/`PUT` — o `PUT` e substituicao completa, entao tudo vai junto. */
export type AddressInput = {
  name?: string
  zipcode: string
  country: string
  state: string
  city: string
  street: string
  number?: string
}

/** Corpo de `PATCH` — so o que mudou. */
export type AddressPatch = Partial<AddressInput>
