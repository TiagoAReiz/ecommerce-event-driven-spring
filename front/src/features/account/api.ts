/** Chamadas HTTP da area de conta, num lugar so para as paginas nao repetirem path. */

import { api } from '../../lib/api'
import type { Page } from '../../types/api'
import type { Address, AddressInput, AddressPatch, ProfilePatch, UserProfile } from './types'

/** Query keys comecando em `account`, como o time pediu. */
export const accountKeys = {
  me: ['account', 'me'] as const,
  addresses: (page: number) => ['account', 'addresses', page] as const,
  address: (id: string) => ['account', 'addresses', 'detail', id] as const,
}

export function fetchMe(): Promise<UserProfile> {
  return api.get<UserProfile>('/users/me')
}

export function updateMe(body: ProfilePatch): Promise<UserProfile> {
  return api.patch<UserProfile>('/users/me', body)
}

export function deleteMe(): Promise<void> {
  return api.delete<void>('/users/me')
}

const ADDRESS_PAGE_SIZE = 20

export function fetchAddresses(page: number): Promise<Page<Address>> {
  return api.get<Page<Address>>('/users/me/addresses', {
    query: { page, size: ADDRESS_PAGE_SIZE, sort: 'createdAt,desc' },
  })
}

export function fetchAddress(id: string): Promise<Address> {
  return api.get<Address>(`/users/me/addresses/${id}`)
}

export function createAddress(body: AddressInput): Promise<Address> {
  return api.post<Address>('/users/me/addresses', body)
}

export function replaceAddress(id: string, body: AddressInput): Promise<Address> {
  return api.put<Address>(`/users/me/addresses/${id}`, body)
}

export function patchAddress(id: string, body: AddressPatch): Promise<Address> {
  return api.patch<Address>(`/users/me/addresses/${id}`, body)
}

export function removeAddress(id: string): Promise<void> {
  return api.delete<void>(`/users/me/addresses/${id}`)
}
