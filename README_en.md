# Create: Pocket Factory Documentation

## 1. Project Positioning

Create: Pocket Factory is an independent dimension-based factory mod for Create automation players. It is designed to reduce GPU load.

Core ideas:

1. Move automation machines with heavy rendering cost into a separate dimension.
2. After the player leaves the factory, the machines keep running and still consume TPS, but they no longer occupy the rendering budget of the player's active area, which can significantly reduce FPS pressure.
3. Provide convenient ways to transfer fluids, items, and stress across dimensions.

## 2. Design Overview

1. Players can enter a box decorated with a white-gray checkerboard pattern in a new dimension through the entrance. This box is the Pocket Factory.
2. The Pocket Factory keeps its chunk footprint as small as possible and remains permanently loaded so that the player's machines can keep working.
3. When leaving the factory, players will first try to return to their previously recorded position. If that position is invalid, they will be sent back to the Overworld spawn point.
4. When entering the factory, the game will first try to teleport the player onto the first carpet it finds. If none is found, it will look for another safe standing position.
5. Players can spend items to expand the size of the factory.
6. The factory dimension is kept relatively bright at all times. The outer shell of the factory box is built with luminous blocks so that players do not suffer from poor visibility when entering the Pocket Factory, with or without shaders.
7. Players can use the core to right-click certain Create blocks and convert them into linked variants for cross-dimensional automation transfer.

## 3. Config Options

- Allows players to configure whether linking is restricted to inside-versus-outside factory pairing only.

## 4. Item Overview

### Factory Core

Used as the crafting ingredient for the factory entrance, and also as the main tool for creating most linked blocks:

1. Right-click Pocket Factory Block A while holding the core. This means the white blocks on the walls, ceiling, or floor inside the Pocket Factory. This triggers factory expansion. Horizontal expansion consumes 1 core each time; vertical expansion is fixed at 1 block per use.
2. Right-click two Item Vaults to convert both multiblock structures into Linked Item Vaults.
3. Right-click two Fluid Tanks to convert both multiblock structures into Linked Fluid Tanks.
4. Right-click two vertical Chutes to create a Linked Chute binding.
5. Right-click two Clutches to create a Linked Clutch binding.
6. Right-click two Mechanical Pumps to create a Linked Pump binding.
7. Can also be used to craft the factory entrance.

### Factory Entrance

- This is the standard way for normal players to enter a Pocket Factory. Place it and right-click it to teleport inside.
- The block contains a miniature model of the bound Pocket Factory. Some blocks may fail to render, but it is generally enough to identify which factory this entrance belongs to.
- Each placed entrance creates a new Pocket Factory for binding. After being broken, the binding remains, and placing it again allows access to the same factory.
- If a Factory Entrance is lost for some reason, a corresponding entrance can be restored with commands. Otherwise, that factory may become inaccessible unless cheats are enabled.

### Linked Blocks

- They cannot be crafted directly. They can only be created by converting the corresponding normal block with the Factory Core.
- When broken, they drop the normal version of the item, and the linked block on the other side reverts to its vanilla counterpart.
- Bindings are one-to-one. Container-type blocks share capacity, while logistics-type blocks cross-route their transfers.
- Their appearance remains the same as the vanilla or original Create version.
- Cross-dimensional linking is supported.
- Due to implementation limits, this mod does not provide linked Belts, Chain Drives, or other logistics devices that introduce full item-network behavior.

#### Item Vault

- Right-click two Item Vaults with the Factory Core to convert them into Linked Item Vaults.
- The entire multiblock structure is converted.
- The inventories of the linked Item Vaults are shared, and their total capacity equals the sum of both structures.
- When any block in either linked Item Vault structure is broken, the current multiblock structure is destroyed and dropped, while the linked structure on the other side reverts to a normal Item Vault structure.

#### Fluid Tank

- Right-click two Fluid Tanks with the Factory Core to convert them into Linked Fluid Tanks.
- The entire multiblock structure is converted.
- The contents of the linked Fluid Tanks are shared, and the total capacity equals the sum of both structures.
- When any block in either linked Fluid Tank structure is broken, the current multiblock structure is destroyed and dropped, while the linked structure on the other side reverts to a normal Fluid Tank structure.

#### Chute

- Right-click two Chutes with the Factory Core to convert them into Linked Chutes. Slanted Chutes are not supported.
- The outputs of Linked Chute A and B are swapped. Items entering A will drop from below B, and upward airflow entering from below B will continue upward from above A.
- When either linked Chute is broken, the linked block on the other side reverts to a normal Chute.

#### Mechanical Pump

- Right-click two Mechanical Pumps with the Factory Core to convert them into Linked Mechanical Pumps.
- The outputs of Linked Pump A and B are swapped. Fluid entering A will be pumped out from the output side of B.
- For performance reasons, pumped fluid cannot be emitted into open air as world fluid blocks.
- When either linked Mechanical Pump is broken, the linked block on the other side reverts to a normal Mechanical Pump.

#### Clutch

- Right-click two Clutches with the Factory Core to convert them into Linked Clutches.
- Linked Clutches have two states: both sides share the same signal, or both sides have different signals. In other words, this depends on whether each side is powered by redstone.
- When both sides are either powered or unpowered, the linked clutch is in straight-through mode.
- When only one side is powered, the linked clutch is in cross mode.
- In straight-through mode, the linked clutch behaves like a Shaft.
- In cross mode, the outputs of Linked Clutch A and B are swapped. Stress and rotational speed entering A's head are output from B's tail, input entering B's head is output from A's tail, input entering A's tail is output from B's head, and input entering B's tail is output from A's head.
- If one side of a crossed connection already has an input, the corresponding side on the other end is forced to be an output. For example, if A's head is already acting as an input, B's tail can no longer accept input.
- When either linked Clutch is broken, the linked block on the other side reverts to a normal Clutch.