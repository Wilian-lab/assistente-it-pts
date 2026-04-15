import { expect, test } from '@playwright/test'

test('login admin e acesso ao dashboard', async ({ page }) => {
  await page.goto('/login')

  await page.getByLabel('Email').fill('admin@teste.com')
  await page.getByLabel('Senha').fill('admin12345')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page).toHaveURL('http://127.0.0.1:5173/')
  await expect(page.getByRole('heading', { name: 'Painel do usuário' })).toBeVisible()
  await expect(page.getByRole('banner').getByText('ADMIN')).toBeVisible()
  await expect(page.getByRole('main').getByText('Administrador Sistema')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Admin' })).toBeVisible()
})
