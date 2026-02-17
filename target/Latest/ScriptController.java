@Controller
@RequestMapping("/compare")
public class CompareController {

    private final TableCompareService service;

    public CompareController(TableCompareService service) {
        this.service = service;
    }

    // Initial page load
    @GetMapping
    public String showPage(Model model) {
        model.addAttribute("request", new CompareRequest());
        return "compare";
    }

    // Form submit
    @PostMapping
    public String compare(
            @ModelAttribute("request") CompareRequest request,
            Model model) {

        TableDiffResponseDTO diff = service.compareTable(
                request.getTableName(),
                request.getIncludeColumns(),
                request.getExcludeColumns(),
                request.getFallbackKeys());

        model.addAttribute("diff", diff);
        return "compare";
    }
}
