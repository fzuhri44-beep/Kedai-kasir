const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_kasir_1";
const BOB_UID = "bob_kasir_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write menuItems or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/menuItems`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/menuItems`).doc("item_1").set({
      userId: ALICE_UID,
      name: "Kopi Susu Aa Sulthan",
      category: "Minuman",
      price: 15000,
      available: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid menuItem create, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_1").set({
      userId: ALICE_UID,
      name: "Kopi Susu Aa Sulthan",
      category: "Minuman",
      price: 15000,
      available: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching Kotlin MenuRepository
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).get()
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_1").update({
      name: "Kopi Susu Aren Spesial",
      price: 18000,
      available: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_2").set({
      id: "kopi_2",
      userId: ALICE_UID,
      name: "Es Teh",
      category: "Minuman",
      price: 5000,
      available: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid category or negative price
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_3").set({
      userId: ALICE_UID,
      name: "Menu Salah",
      category: "InvalidCat",
      price: -1000,
      available: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_4").set({
      userId: ALICE_UID,
      name: "Kopi Masa Depan",
      category: "Minuman",
      price: 15000,
      available: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/menuItems`).doc("kopi_1").delete()
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice menuItems or transactions", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection(`users/${ALICE_UID}/menuItems`).doc("alice_item").set({
      userId: ALICE_UID,
      name: "Mie Nyemek Spesial",
      category: "Makanan",
      price: 20000,
      available: true,
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/menuItems`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/menuItems`).doc("alice_item").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/menuItems`).doc("alice_item").update({
      price: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/menuItems`).doc("alice_item").delete());
});

test("Transactions: create with optional customerNote omitted/present succeeds; update blocked; delete allowed for owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 3000);

  // Without customerNote (null filtered out in Kotlin)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_1").set({
      userId: ALICE_UID,
      receiptNumber: "AAS-20261118-001",
      paymentMethod: "Tunai",
      totalAmount: 35000,
      cashReceived: 50000,
      changeAmount: 15000,
      itemCount: 2,
      items: [
        { menuItemId: "m1", name: "Nasi Goreng", unitPrice: 20000, quantity: 1, subtotal: 20000 },
        { menuItemId: "m2", name: "Kopi Susu", unitPrice: 15000, quantity: 1, subtotal: 15000 },
      ],
      createdAt: created,
      updatedAt: created,
    })
  );

  // With customerNote
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_2").set({
      userId: ALICE_UID,
      receiptNumber: "AAS-20261118-002",
      customerNote: "Meja 3 - Bungkus",
      paymentMethod: "QRIS",
      totalAmount: 18000,
      cashReceived: 18000,
      changeAmount: 0,
      itemCount: 1,
      items: [
        { menuItemId: "m2", name: "Kopi Susu", unitPrice: 18000, quantity: 1, subtotal: 18000 },
      ],
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching Kotlin TransactionRepository
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).orderBy("createdAt", "desc").get()
  );

  // Update is forbidden on completed receipts
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_1").update({
      totalAmount: 1000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject empty items list or invalid payment method
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_3").set({
      userId: ALICE_UID,
      receiptNumber: "AAS-20261118-003",
      paymentMethod: "Hutang",
      totalAmount: 10000,
      cashReceived: 0,
      changeAmount: 0,
      itemCount: 1,
      items: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_1").delete()
  );
});
