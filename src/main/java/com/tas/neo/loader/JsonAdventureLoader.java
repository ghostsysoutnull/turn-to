package com.tas.neo.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.*;
import com.tas.neo.domain.adventure.event.*;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Direction;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.location.Passage;
import com.tas.neo.domain.party.DefeatConsequence;
import com.tas.neo.domain.party.GameOverConsequence;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.NavigateConsequence;
import com.tas.neo.domain.party.PartyMemberDefinition;
import com.tas.neo.domain.party.RemoveConsequence;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.mechanics.DiceFormula;
import com.tas.neo.mechanics.DiceStatDefinition;
import com.tas.neo.mechanics.FixedStatDefinition;
import com.tas.neo.mechanics.StatDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class JsonAdventureLoader implements AdventureLoader {

    private final Path adventuresRoot;
    private final ObjectMapper mapper;

    public JsonAdventureLoader(Path adventuresRoot) {
        this.adventuresRoot = adventuresRoot;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Adventure load(String adventureId) throws AdventureLoadException {
        Path file = adventuresRoot.resolve(adventureId + ".json");
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new AdventureLoadException("Cannot read adventure file: " + file, e);
        }

        AdventureDto dto = parseAdventureDto(root);
        validate(dto);
        return buildAdventure(dto);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private AdventureDto parseAdventureDto(JsonNode root) throws AdventureLoadException {
        String id = requireText(root, "id");
        String title = requireText(root, "title");
        String description = root.path("description").asText("");

        if (!root.has("startSection")) {
            throw new AdventureLoadException("Missing required field: startSection");
        }
        int startSection = root.get("startSection").asInt();
        int initialProvisions = root.path("initialProvisions").asInt(0);

        List<SectionDto> sections = parseSections(root.path("sections"));
        List<ItemDto> items = parseItems(root.path("items"));
        List<PartyMemberDto> partyMembers = parsePartyMembers(root.path("partyMembers"));
        List<String> combatSystems = parseStringList(root.path("combatSystems"));
        List<GridDto> grids = parseGrids(root.path("grids"));
        ScriptBlock scripts = parseScriptBlock(root.path("scripts"));

        return new AdventureDto(id, title, description, startSection, initialProvisions,
                sections, items, partyMembers, combatSystems, grids, scripts);
    }

    private List<SectionDto> parseSections(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<SectionDto> result = new ArrayList<>();
        for (JsonNode sn : node) {
            result.add(parseSection(sn));
        }
        return result;
    }

    private SectionDto parseSection(JsonNode node) throws AdventureLoadException {
        int number = node.get("number").asInt();
        SectionType type = SectionType.valueOf(node.path("type").asText("NORMAL"));
        String narrative = node.path("narrative").asText("");
        List<SectionEventDto> events = parseSectionEventDtos(node.path("events"));
        List<ChoiceDto> choices = parseChoices(node.path("choices"));
        ScriptBlock scripts = parseScriptBlock(node.path("scripts"));
        return new SectionDto(number, type, narrative, events, choices, scripts);
    }

    private List<SectionEventDto> parseSectionEventDtos(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<SectionEventDto> result = new ArrayList<>();
        for (JsonNode en : node) {
            result.add(parseSectionEventDto(en));
        }
        return result;
    }

    private SectionEventDto parseSectionEventDto(JsonNode node) throws AdventureLoadException {
        String type = requireText(node, "type");
        SectionEvent event = switch (type) {
            case "STAT_CHANGE" -> {
                AttributeType attr = AttributeType.valueOf(requireText(node, "attribute"));
                int delta = node.get("delta").asInt();
                yield new StatChangeEvent(attr, delta);
            }
            case "ITEM" -> {
                String itemName = requireText(node, "itemName");
                String actionStr = requireText(node, "action");
                ItemAction action;
                try {
                    action = ItemAction.valueOf(actionStr);
                } catch (IllegalArgumentException e) {
                    throw new AdventureLoadException("Unknown item action: " + actionStr);
                }
                int qty = node.path("quantity").asInt(1);
                yield new ItemEvent(itemName, action, qty);
            }
            case "ITEM_GAIN" -> {
                String itemName = node.path("itemName").isMissingNode()
                        ? requireText(node, "item") : requireText(node, "itemName");
                int qty = node.path("quantity").asInt(1);
                yield new ItemEvent(itemName, ItemAction.GAIN, qty);
            }
            case "ITEM_LOSS" -> {
                String itemName = node.path("itemName").isMissingNode()
                        ? requireText(node, "item") : requireText(node, "itemName");
                int qty = node.path("quantity").asInt(1);
                yield new ItemEvent(itemName, ItemAction.LOSS, qty);
            }
            case "COMBAT" -> {
                String system = node.path("system").isMissingNode() || node.path("system").isNull()
                        ? "personal" : node.path("system").asText("personal");
                boolean simultaneous = node.path("simultaneous").asBoolean(false);
                List<String> participants = parseStringList(node.path("participants"));
                List<Creature> opponents;
                if (!node.path("enemy").isMissingNode()) {
                    opponents = parseCreatures(node.path("enemy"));
                } else if (!node.path("enemies").isMissingNode()) {
                    opponents = parseCreatures(node.path("enemies"));
                } else {
                    opponents = parseCreatures(node.path("opponents"));
                }
                Map<String, Object> params = parseParams(node.path("params"));
                ScriptBlock scripts = parseScriptBlock(node.path("scripts"));
                int successSection = node.path("successSection").asInt(0);
                int failureSection = node.path("failureSection").isMissingNode()
                        ? node.path("failSection").asInt(0)
                        : node.path("failureSection").asInt(0);
                yield new CombatEvent(system, participants, opponents, simultaneous, params, scripts,
                        successSection, failureSection);
            }
            case "LUCK_TEST" -> {
                int success = node.get("successSection").asInt();
                yield new LuckTestEvent(success, failSectionFrom(node));
            }
            case "SKILL_TEST" -> {
                int success = node.get("successSection").asInt();
                yield new SkillTestEvent(success, failSectionFrom(node));
            }
            case "NAVIGATE" -> {
                int target = node.get("targetSection").asInt();
                yield new NavigateEvent(target);
            }
            case "GOLD_CHANGE" -> {
                int delta = node.get("delta").asInt();
                yield new GoldChangeEvent(delta);
            }
            default -> throw new AdventureLoadException("Unknown event type: " + type);
        };
        // Collect item names referenced
        String referencedItem = switch (event) {
            case ItemEvent e -> e.itemName();
            default -> null;
        };
        // Collect participantIds
        List<String> participantIds = switch (event) {
            case CombatEvent e -> e.participantIds();
            default -> List.of();
        };
        return new SectionEventDto(event, referencedItem, participantIds);
    }

    private List<Creature> parseCreatures(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (node.isObject()) {
            return List.of(new Creature(node.path("name").asText(),
                    node.path("skill").asInt(), node.path("stamina").asInt()));
        }
        List<Creature> result = new ArrayList<>();
        for (JsonNode cn : node) {
            result.add(new Creature(cn.path("name").asText(), cn.path("skill").asInt(), cn.path("stamina").asInt()));
        }
        return result;
    }

    private List<ChoiceDto> parseChoices(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<ChoiceDto> result = new ArrayList<>();
        for (JsonNode cn : node) {
            result.add(parseChoiceDto(cn));
        }
        return result;
    }

    private ChoiceDto parseChoiceDto(JsonNode node) throws AdventureLoadException {
        String text = node.path("text").asText();
        boolean hasTargetSection = node.has("targetSection") && !node.get("targetSection").isNull();
        boolean hasToGrid = node.has("toGrid") && !node.get("toGrid").isNull();
        boolean hasToCell = node.has("toCell") && !node.get("toCell").isNull();
        Condition condition = node.has("condition") ? parseCondition(node.get("condition")) : null;
        boolean conflicting = hasTargetSection && (hasToGrid || hasToCell);
        Integer targetSection = hasTargetSection ? node.get("targetSection").asInt() : null;
        String gridId = (hasToGrid) ? node.get("toGrid").asText() : null;
        String cellId = (hasToCell) ? node.get("toCell").asText() : null;
        return new ChoiceDto(text, gridId, cellId, targetSection, conflicting, condition);
    }

    private Condition parseCondition(JsonNode node) throws AdventureLoadException {
        String type = requireText(node, "type");
        return switch (type) {
            case "HAS_ITEM" -> new HasItemCondition(itemNameFrom(node));
            case "LACKS_ITEM" -> new LacksItemCondition(itemNameFrom(node));
            case "STAT" -> new StatCondition(
                    AttributeType.valueOf(requireText(node, "attribute")),
                    ComparisonType.valueOf(requireText(node, "comparison")),
                    node.get("threshold").asInt());
            case "GOLD", "HAS_GOLD" -> {
                int minimum = node.path("minimum").isMissingNode()
                        ? node.get("amount").asInt() : node.get("minimum").asInt();
                yield new GoldCondition(minimum);
            }
            case "PARTY_STAT" -> new PartyStatCondition(
                    requireText(node, "memberId"), requireText(node, "statName"),
                    ComparisonType.valueOf(requireText(node, "comparison")),
                    node.get("threshold").asInt());
            case "PARTY_MEMBER_ACTIVE" -> new PartyMemberActiveCondition(requireText(node, "memberId"));
            case "PARTY_MEMBER_WAITING" -> new PartyMemberWaitingCondition(requireText(node, "memberId"));
            case "PARTY_MEMBER_REMOVED" -> new PartyMemberRemovedCondition(requireText(node, "memberId"));
            case "STATE_EQUALS" -> new StateEqualsCondition(requireText(node, "key"), node.get("value").asText());
            case "STATE_NOT_EQUALS" -> new StateNotEqualsCondition(requireText(node, "key"), node.get("value").asText());
            default -> throw new AdventureLoadException("Unknown condition type: " + type);
        };
    }

    private List<ItemDto> parseItems(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<ItemDto> result = new ArrayList<>();
        for (JsonNode in : node) {
            String name = in.path("name").asText();
            String description = in.path("description").asText("");
            ItemCategory category = in.has("category")
                    ? ItemCategory.valueOf(in.get("category").asText()) : ItemCategory.PASSIVE;
            boolean countable = in.path("countable").asBoolean(false);
            ScriptBlock scripts = parseScriptBlock(in.path("scripts"));
            result.add(new ItemDto(name, description, category, countable, scripts));
        }
        return result;
    }

    private List<PartyMemberDto> parsePartyMembers(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<PartyMemberDto> result = new ArrayList<>();
        for (JsonNode pn : node) {
            result.add(parsePartyMember(pn));
        }
        return result;
    }

    private PartyMemberDto parsePartyMember(JsonNode node) throws AdventureLoadException {
        String id = requireText(node, "id");
        String displayName = node.path("displayName").asText(id);
        String lifeStat = node.path("lifeStat").asText("STAMINA");
        MemberState initialState = node.has("initialState")
                ? MemberState.valueOf(node.get("initialState").asText()) : MemberState.ACTIVE;
        DefeatConsequence onDefeat = parseDefeatConsequence(node.path("onDefeat"));
        Map<String, StatDefinition> stats = parseStats(node.path("stats"));
        return new PartyMemberDto(id, displayName, lifeStat, initialState, onDefeat, stats);
    }

    private DefeatConsequence parseDefeatConsequence(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return new GameOverConsequence("Game over.");
        String type = node.path("type").asText("GAME_OVER");
        return switch (type) {
            case "GAME_OVER" -> new GameOverConsequence(node.path("message").asText("Game over."));
            case "REMOVE" -> new RemoveConsequence(node.path("message").asText(""));
            case "NAVIGATE" -> new NavigateConsequence(node.get("section").asInt(), node.path("message").asText(""));
            default -> new GameOverConsequence("Game over.");
        };
    }

    private Map<String, StatDefinition> parseStats(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return Map.of();
        Map<String, StatDefinition> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), parseStatDefinition(entry.getValue()));
        }
        return result;
    }

    private StatDefinition parseStatDefinition(JsonNode node) {
        if (node.isNumber()) {
            int v = node.asInt();
            return new FixedStatDefinition(v, v);
        }
        if (node.has("formula")) {
            String formula = node.get("formula").asText();
            int fixedMax = node.path("max").asInt(-1);
            if (fixedMax >= 0) {
                return new DiceStatDefinition(DiceFormula.parse(formula), OptionalInt.of(fixedMax));
            }
            return new DiceStatDefinition(DiceFormula.parse(formula), OptionalInt.empty());
        }
        int initial = node.path("initial").asInt(0);
        int max = node.path("max").asInt(initial);
        return new FixedStatDefinition(initial, max);
    }

    private List<GridDto> parseGrids(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<GridDto> result = new ArrayList<>();
        for (JsonNode gn : node) {
            result.add(parseGrid(gn));
        }
        return result;
    }

    private GridDto parseGrid(JsonNode node) throws AdventureLoadException {
        String id = requireText(node, "id");
        int width = node.get("width").asInt();
        int height = node.get("height").asInt();
        int floors = node.path("floors").asInt(1);
        List<CellDto> cells = parseCells(node.path("cells"));
        return new GridDto(id, width, height, floors, cells);
    }

    private List<CellDto> parseCells(JsonNode node) throws AdventureLoadException {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<CellDto> result = new ArrayList<>();
        for (JsonNode cn : node) {
            result.add(parseCell(cn));
        }
        return result;
    }

    private CellDto parseCell(JsonNode node) throws AdventureLoadException {
        String id = node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null;
        int x = node.get("x").asInt();
        int y = node.get("y").asInt();
        int z = node.path("z").asInt(0);
        String narrative = node.path("narrative").asText("");
        List<SectionEventDto> events = parseSectionEventDtos(node.path("events"));
        ScriptBlock scripts = parseScriptBlock(node.path("scripts"));
        Map<Direction, PassageDto> passages = parsePassages(node.path("passages"));
        return new CellDto(id, x, y, z, narrative, events, scripts, passages);
    }

    private Map<Direction, PassageDto> parsePassages(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return Map.of();
        Map<Direction, PassageDto> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            Direction dir = Direction.valueOf(entry.getKey().toUpperCase());
            JsonNode pn = entry.getValue();
            String label = pn.has("label") ? pn.get("label").asText() : null;
            Integer toSection = pn.has("toSection") && !pn.get("toSection").isNull()
                    ? pn.get("toSection").asInt() : null;
            result.put(dir, new PassageDto(label, toSection));
        }
        return result;
    }

    private ScriptBlock parseScriptBlock(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return ScriptBlock.empty();
        Map<String, String> hooks = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            hooks.put(entry.getKey(), entry.getValue().asText());
        }
        return new ScriptBlock(hooks);
    }

    private List<String> parseStringList(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode n : node) result.add(n.asText());
        return result;
    }

    private Map<String, Object> parseParams(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    private int failSectionFrom(JsonNode node) throws AdventureLoadException {
        if (!node.path("failSection").isMissingNode()) return node.get("failSection").asInt();
        if (!node.path("failureSection").isMissingNode()) return node.get("failureSection").asInt();
        throw new AdventureLoadException("Missing required field: failSection");
    }

    private String itemNameFrom(JsonNode node) throws AdventureLoadException {
        if (node.has("itemName") && !node.get("itemName").isNull()) return node.get("itemName").asText();
        if (node.has("item") && !node.get("item").isNull()) return node.get("item").asText();
        throw new AdventureLoadException("Missing required field: itemName");
    }

    private String requireText(JsonNode node, String field) throws AdventureLoadException {
        if (!node.has(field) || node.get(field).isNull()) {
            throw new AdventureLoadException("Missing required field: " + field);
        }
        return node.get(field).asText();
    }

    // -------------------------------------------------------------------------
    // Validation (operates on DTOs, before domain object construction)
    // -------------------------------------------------------------------------

    private void validate(AdventureDto dto) throws AdventureLoadException {
        Set<Integer> sectionNums = dto.sections.stream()
                .map(SectionDto::number).collect(Collectors.toSet());

        // startSection must exist — skip when sections is empty (pre-authoring skeleton)
        if (!sectionNums.isEmpty() && !sectionNums.contains(dto.startSection)) {
            throw new AdventureLoadException(
                    "startSection " + dto.startSection + " does not exist in sections");
        }

        Set<String> itemNames = dto.items.stream()
                .map(ItemDto::name).collect(Collectors.toSet());

        Set<String> partyIds = dto.partyMembers.stream()
                .map(PartyMemberDto::id).collect(Collectors.toSet());

        // Build a map: gridId -> set of cell ids (for grid target validation)
        Map<String, Set<String>> gridCellIds = new LinkedHashMap<>();

        // Validate grids
        Set<String> gridIds = new HashSet<>();
        for (GridDto grid : dto.grids) {
            if (!gridIds.add(grid.id)) {
                throw new AdventureLoadException("Duplicate grid id: " + grid.id);
            }
            validateGridDto(grid, sectionNums);
            Set<String> cellIdSet = grid.cells.stream()
                    .filter(c -> c.id != null)
                    .map(c -> c.id)
                    .collect(Collectors.toSet());
            gridCellIds.put(grid.id, cellIdSet);
        }

        // Validate sections
        for (SectionDto section : dto.sections) {
            validateSectionDto(section, sectionNums, itemNames, partyIds, gridIds, gridCellIds);
        }
    }

    private void validateSectionDto(SectionDto section, Set<Integer> allSections,
                                     Set<String> itemNames, Set<String> partyIds,
                                     Set<String> gridIds, Map<String, Set<String>> gridCellIds)
            throws AdventureLoadException {
        // Validate events
        for (SectionEventDto eventDto : section.events) {
            validateEventDto(eventDto, allSections, itemNames, partyIds);
        }

        // Validate choices
        for (ChoiceDto choice : section.choices) {
            validateChoiceDto(choice, allSections, gridIds, gridCellIds);
        }

        // NORMAL section must have an exit
        if (section.type == SectionType.NORMAL) {
            boolean hasChoices = !section.choices.isEmpty();
            boolean hasNavigateEvent = section.events.stream()
                    .anyMatch(e -> e.event instanceof NavigateEvent
                               || e.event instanceof LuckTestEvent
                               || e.event instanceof SkillTestEvent
                               || (e.event instanceof CombatEvent ce
                                   && (ce.successSection() > 0 || ce.failureSection() > 0)));
            boolean hasOnEnterScript = section.scripts.get("onEnter").isPresent();
            if (!hasChoices && !hasNavigateEvent && !hasOnEnterScript) {
                throw new AdventureLoadException(
                        "NORMAL section " + section.number
                                + " has no exit (no choices, no NAVIGATE event, no onEnter script)");
            }
        }
    }

    private void validateEventDto(SectionEventDto eventDto, Set<Integer> allSections,
                                   Set<String> itemNames, Set<String> partyIds)
            throws AdventureLoadException {
        switch (eventDto.event) {
            case LuckTestEvent e -> {
                if (!allSections.contains(e.successSection())) {
                    throw new AdventureLoadException(
                            "LUCK_TEST successSection " + e.successSection() + " does not exist");
                }
                if (!allSections.contains(e.failSection())) {
                    throw new AdventureLoadException(
                            "LUCK_TEST failSection " + e.failSection() + " does not exist");
                }
            }
            case SkillTestEvent e -> {
                if (!allSections.contains(e.successSection())) {
                    throw new AdventureLoadException(
                            "SKILL_TEST successSection " + e.successSection() + " does not exist");
                }
                if (!allSections.contains(e.failSection())) {
                    throw new AdventureLoadException(
                            "SKILL_TEST failSection " + e.failSection() + " does not exist");
                }
            }
            case NavigateEvent e -> {
                if (!allSections.contains(e.targetSection())) {
                    throw new AdventureLoadException(
                            "NAVIGATE targetSection " + e.targetSection() + " does not exist");
                }
            }
            case CombatEvent e -> {
                for (Creature c : e.opponents()) {
                    if (c.skill() <= 0) {
                        throw new AdventureLoadException(
                                "Creature '" + c.name() + "' has invalid SKILL: " + c.skill());
                    }
                    if (c.stamina() <= 0) {
                        throw new AdventureLoadException(
                                "Creature '" + c.name() + "' has invalid STAMINA: " + c.stamina());
                    }
                }
                for (String pid : e.participantIds()) {
                    if (!partyIds.contains(pid)) {
                        throw new AdventureLoadException(
                                "CombatEvent participantId '" + pid + "' not found in partyMembers");
                    }
                }
                if (e.successSection() > 0 && !allSections.contains(e.successSection())) {
                    throw new AdventureLoadException(
                            "COMBAT successSection " + e.successSection() + " does not exist");
                }
                if (e.failureSection() > 0 && !allSections.contains(e.failureSection())) {
                    throw new AdventureLoadException(
                            "COMBAT failureSection " + e.failureSection() + " does not exist");
                }
            }
            case ItemEvent e -> {
                if (!itemNames.contains(e.itemName())) {
                    throw new AdventureLoadException(
                            "Item '" + e.itemName() + "' referenced in event is not in the adventure items list");
                }
            }
            default -> { /* StatChangeEvent, GoldChangeEvent — no cross-refs */ }
        }
    }

    private void validateChoiceDto(ChoiceDto choice, Set<Integer> allSections,
                                    Set<String> gridIds, Map<String, Set<String>> gridCellIds)
            throws AdventureLoadException {
        if (choice.conflicting) {
            throw new AdventureLoadException(
                    "Choice '" + choice.text + "' declares both targetSection and toGrid/toCell");
        }
        if (choice.gridId != null) {
            if (!gridIds.contains(choice.gridId)) {
                throw new AdventureLoadException(
                        "Choice toGrid '" + choice.gridId + "' does not exist");
            }
            Set<String> cellIds = gridCellIds.get(choice.gridId);
            if (cellIds == null || !cellIds.contains(choice.cellId)) {
                throw new AdventureLoadException(
                        "Choice toCell '" + choice.cellId + "' does not exist in grid '" + choice.gridId + "'");
            }
        } else if (choice.targetSection != null) {
            if (!allSections.contains(choice.targetSection)) {
                throw new AdventureLoadException(
                        "Choice targetSection " + choice.targetSection + " does not exist");
            }
        }
    }

    private void validateGridDto(GridDto grid, Set<Integer> allSections) throws AdventureLoadException {
        Set<String> cellIds = new HashSet<>();
        Set<String> cellCoords = new HashSet<>();
        // Build coord set for passage validation
        Set<String> occupiedCoords = grid.cells.stream()
                .map(c -> c.x + "," + c.y + "," + c.z)
                .collect(Collectors.toSet());

        for (CellDto cell : grid.cells) {
            // Check bounds
            if (cell.x < 0 || cell.x >= grid.width
                    || cell.y < 0 || cell.y >= grid.height
                    || cell.z < 0 || cell.z >= grid.floors) {
                throw new AdventureLoadException(
                        "Cell at (" + cell.x + "," + cell.y + "," + cell.z
                                + ") is outside grid '" + grid.id + "' bounds");
            }

            // Duplicate coordinates
            String coord = cell.x + "," + cell.y + "," + cell.z;
            if (!cellCoords.add(coord)) {
                throw new AdventureLoadException(
                        "Duplicate cell coordinates (" + coord + ") in grid '" + grid.id + "'");
            }

            // Duplicate cell ids
            if (cell.id != null) {
                if (!cellIds.add(cell.id)) {
                    throw new AdventureLoadException(
                            "Duplicate cell id '" + cell.id + "' in grid '" + grid.id + "'");
                }
            }

            // Validate passages
            for (Map.Entry<Direction, PassageDto> entry : cell.passages.entrySet()) {
                Direction dir = entry.getKey();
                PassageDto passage = entry.getValue();
                if (passage.toSection != null) {
                    if (!allSections.contains(passage.toSection)) {
                        throw new AdventureLoadException(
                                "Passage " + dir + " from cell (" + coord
                                        + ") in grid '" + grid.id
                                        + "' toSection " + passage.toSection + " does not exist");
                    }
                } else {
                    int tx = cell.x + dir.dx();
                    int ty = cell.y + dir.dy();
                    int tz = cell.z + dir.dz();
                    String targetCoord = tx + "," + ty + "," + tz;
                    if (!occupiedCoords.contains(targetCoord)) {
                        throw new AdventureLoadException(
                                "Passage " + dir + " from cell (" + coord
                                        + ") in grid '" + grid.id
                                        + "' targets coordinate (" + targetCoord + ") which has no cell");
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Domain object construction (after validation)
    // -------------------------------------------------------------------------

    private Adventure buildAdventure(AdventureDto dto) {
        List<Item> items = dto.items.stream()
                .map(i -> new Item(i.name, i.description, i.category, i.countable, i.scripts))
                .collect(Collectors.toList());

        List<PartyMemberDefinition> partyDefs = dto.partyMembers.stream()
                .map(p -> new PartyMemberDefinition(p.id, p.displayName, p.lifeStat,
                        p.onDefeat, p.initialState, p.stats))
                .collect(Collectors.toList());

        List<Grid> grids = dto.grids.stream()
                .map(this::buildGrid)
                .collect(Collectors.toList());

        List<Section> sections = dto.sections.stream()
                .map(this::buildSection)
                .collect(Collectors.toList());

        return new Adventure(dto.id, dto.title, dto.description, dto.startSection,
                dto.initialProvisions, sections, items, partyDefs, dto.combatSystems, grids, dto.scripts);
    }

    private Grid buildGrid(GridDto dto) {
        Map<String, Cell> cellMap = new LinkedHashMap<>();
        for (CellDto cd : dto.cells) {
            Cell cell = buildCell(cd);
            String key = cd.x + "," + cd.y + "," + cd.z;
            cellMap.put(key, cell);
        }
        return new Grid(dto.id, dto.width, dto.height, dto.floors, cellMap);
    }

    private Cell buildCell(CellDto dto) {
        Map<Direction, Passage> passages = new LinkedHashMap<>();
        for (Map.Entry<Direction, PassageDto> entry : dto.passages.entrySet()) {
            PassageDto pd = entry.getValue();
            passages.put(entry.getKey(), new Passage(
                    Optional.ofNullable(pd.label),
                    Optional.empty(),
                    Optional.ofNullable(pd.toSection)
            ));
        }
        List<SectionEvent> events = dto.events.stream()
                .map(e -> e.event)
                .collect(Collectors.toList());
        return new Cell(
                Optional.ofNullable(dto.id),
                dto.x, dto.y, dto.z,
                dto.narrative,
                events,
                dto.scripts,
                passages,
                List.of()
        );
    }

    private Section buildSection(SectionDto dto) {
        List<SectionEvent> events = dto.events.stream()
                .map(e -> e.event)
                .collect(Collectors.toList());
        List<Choice> choices = dto.choices.stream()
                .map(this::buildChoice)
                .collect(Collectors.toList());
        return new Section(dto.number, dto.narrative, events, choices, dto.type, dto.scripts);
    }

    private Choice buildChoice(ChoiceDto dto) {
        ChoiceTarget target;
        if (dto.gridId != null) {
            target = new GridTarget(dto.gridId, dto.cellId);
        } else {
            target = new SectionTarget(dto.targetSection != null ? dto.targetSection : 0);
        }
        if (dto.condition != null) {
            return Choice.to(dto.text, target, dto.condition);
        }
        return Choice.to(dto.text, target);
    }

    // -------------------------------------------------------------------------
    // DTOs (internal)
    // -------------------------------------------------------------------------

    private record AdventureDto(String id, String title, String description,
                                 int startSection, int initialProvisions,
                                 List<SectionDto> sections, List<ItemDto> items,
                                 List<PartyMemberDto> partyMembers, List<String> combatSystems,
                                 List<GridDto> grids, ScriptBlock scripts) {}

    private record SectionDto(int number, SectionType type, String narrative,
                               List<SectionEventDto> events, List<ChoiceDto> choices,
                               ScriptBlock scripts) {}

    private record SectionEventDto(SectionEvent event, String referencedItemName,
                                    List<String> participantIds) {}

    private record ChoiceDto(String text, String gridId, String cellId,
                              Integer targetSection, boolean conflicting, Condition condition) {}

    private record ItemDto(String name, String description, ItemCategory category,
                            boolean countable, ScriptBlock scripts) {}

    private record PartyMemberDto(String id, String displayName, String lifeStat,
                                   MemberState initialState, DefeatConsequence onDefeat,
                                   Map<String, StatDefinition> stats) {}

    private record GridDto(String id, int width, int height, int floors, List<CellDto> cells) {}

    private record CellDto(String id, int x, int y, int z, String narrative,
                            List<SectionEventDto> events, ScriptBlock scripts,
                            Map<Direction, PassageDto> passages) {}

    private record PassageDto(String label, Integer toSection) {}
}
