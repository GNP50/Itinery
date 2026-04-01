import React from 'react'
import { Filter } from 'lucide-react'

interface Props {
  categories: string[]
  selected: string[]
  onChange: (selected: string[]) => void
}

export function PoiCategoryFilter({ categories, selected, onChange }: Props) {
  const isAllSelected = selected.length === 0 || selected.length === categories.length

  const toggleCategory = (category: string) => {
    if (isAllSelected) {
      // If "All" is selected, clicking a category selects only that one
      onChange([category])
    } else if (selected.includes(category)) {
      // If clicking a selected category, remove it
      const newSelected = selected.filter((c) => c !== category)
      // If removing last category, select all
      onChange(newSelected.length === 0 ? [] : newSelected)
    } else {
      // Add category to selection
      onChange([...selected, category])
    }
  }

  const selectAll = () => {
    onChange([])
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <Filter className="h-4 w-4" />
        Filter by Category
      </div>

      <div className="flex flex-wrap gap-2">
        {/* All button */}
        <button
          onClick={selectAll}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
            isAllSelected
              ? 'bg-primary text-white shadow-sm'
              : 'bg-muted text-muted-foreground hover:bg-muted/80'
          }`}
        >
          All
        </button>

        {/* Category buttons */}
        {categories.map((category) => {
          const isSelected = isAllSelected || selected.includes(category)
          return (
            <button
              key={category}
              onClick={() => toggleCategory(category)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                isSelected && !isAllSelected
                  ? 'bg-primary/20 text-primary border border-primary/30 shadow-sm'
                  : isSelected
                  ? 'bg-primary/10 text-primary/80 hover:bg-primary/15'
                  : 'bg-muted text-muted-foreground hover:bg-muted/80'
              }`}
            >
              {category}
            </button>
          )
        })}
      </div>

      {!isAllSelected && (
        <p className="text-xs text-muted-foreground">
          {selected.length} {selected.length === 1 ? 'category' : 'categories'} selected
        </p>
      )}
    </div>
  )
}
