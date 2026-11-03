package com.untitled.hermesapi.service;

import com.untitled.hermesapi.model.enums.CategoryType;

import java.util.List;

/**
 * Catégories standard créées pour chaque nouvel utilisateur, qui peut ensuite les modifier librement.
 */
final class DefaultCategories {

    record Definition(String name, CategoryType type, String color, String icon, List<String> children) {
    }

    static final List<Definition> ALL = List.of(
            expense("Alimentation", "#E57373", "restaurant",
                    "Courses", "Restaurants", "Cafés & snacks", "Livraison de repas"),
            expense("Logement", "#64B5F6", "home",
                    "Loyer", "Crédit immobilier", "Charges de copropriété", "Électricité & gaz", "Eau",
                    "Assurance habitation", "Travaux & entretien"),
            expense("Transport", "#FFB74D", "directions_car",
                    "Carburant", "Transports en commun", "Entretien du véhicule", "Assurance auto",
                    "Péage & stationnement", "Taxi & VTC", "Train & avion"),
            expense("Santé", "#4DB6AC", "favorite",
                    "Médecin", "Pharmacie", "Mutuelle", "Dentiste & optique"),
            expense("Abonnements & télécoms", "#9575CD", "wifi",
                    "Internet & téléphone", "Streaming", "Logiciels & services en ligne", "Presse"),
            expense("Loisirs", "#F06292", "sports_esports",
                    "Sorties", "Sport", "Culture", "Voyages & vacances", "Jeux"),
            expense("Shopping", "#BA68C8", "shopping_bag",
                    "Vêtements", "High-tech", "Maison & déco", "Cadeaux", "Beauté & soins"),
            expense("Enfants & éducation", "#AED581", "school",
                    "Scolarité", "Garde d'enfants", "Activités enfants", "Formation"),
            expense("Impôts & taxes", "#90A4AE", "account_balance",
                    "Impôt sur le revenu", "Taxe foncière", "Taxe d'habitation", "Autres taxes"),
            expense("Banque & frais", "#A1887F", "credit_card",
                    "Frais bancaires", "Agios", "Intérêts d'emprunt"),
            expense("Divers", "#BDBDBD", "category",
                    "Dons", "Animaux", "Retraits d'espèces", "Autres dépenses"),
            income("Salaire", "#43A047", "work",
                    "Salaire net", "Primes", "Heures supplémentaires"),
            income("Revenus du patrimoine", "#00897B", "savings",
                    "Intérêts", "Dividendes", "Loyers perçus", "Plus-values"),
            income("Aides & allocations", "#7CB342", "volunteer_activism",
                    "Allocations familiales", "Aides au logement", "Chômage", "Autres aides"),
            income("Remboursements", "#039BE5", "replay",
                    "Remboursements santé", "Remboursements de frais", "Remboursements divers"),
            income("Autres revenus", "#8D6E63", "payments",
                    "Ventes", "Cadeaux reçus", "Revenus divers")
    );

    private static Definition expense(String name, String color, String icon, String... children) {
        return new Definition(name, CategoryType.EXPENSE, color, icon, List.of(children));
    }

    private static Definition income(String name, String color, String icon, String... children) {
        return new Definition(name, CategoryType.INCOME, color, icon, List.of(children));
    }

    private DefaultCategories() {
    }
}
