package ${escapeKotlinIdentifiers(packageName)}

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android${SupportPackage}.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

class ${className} : Fragment() {

    companion object {
        fun newInstance() = ${className}()
    }

    private lateinit var viewModel: ${viewModelName}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.${layoutName}, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(${viewModelName}::class.java)
        // TODO: Use the ViewModel
    }

}
